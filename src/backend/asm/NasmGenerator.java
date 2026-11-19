package src.backend.asm;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import src.backend.ir.IRInstruction;
import src.backend.ir.IROpCode;
import src.backend.ir.IRProgram;
import src.backend.ir.Operand;
import src.backend.memory.VirtualMethodTable;
import src.ast.records.ProgramRecord;

public class NasmGenerator {

    private final Map<Operand, Integer> stackOffsets = new LinkedHashMap<>();
    private final Map<String, String> stringLabels = new LinkedHashMap<>();
    private int frameSize;
    private int nextDivisionLabel;
    private int nextStringLabel;
    private int nextGpParameter;
    private int nextXmmParameter;
    private int nextFreeLabel;
    private final VirtualMethodTable virtualMethodTable;

    public NasmGenerator() {
        this.virtualMethodTable = null;
    }

    public NasmGenerator(ProgramRecord program) {
        this.virtualMethodTable = new VirtualMethodTable(program);
    }

    public String generate(IRProgram program) {
        nextFreeLabel = 0;
        StringBuilder asm = new StringBuilder();
        prepareStringLabels(program.instructions());
        appendHeader(asm);

        List<IRInstruction> function = new ArrayList<>();

        for (IRInstruction instruction : program.instructions()) {
            if (isFunctionLabel(instruction)) {
                if (!function.isEmpty()) {
                    emitFunction(asm, function);
                }
                function = new ArrayList<>();
            }

            if (!function.isEmpty()
                    || isFunctionLabel(instruction)) {
                function.add(instruction);
            }
        }

        if (!function.isEmpty()) {
            emitFunction(asm, function);
        }

        String entryFunction = findEntryFunction(program);
        if (entryFunction == null) {
            throw new IllegalStateException(
                    "O programa precisa ter main(String[]) ou main() para gerar um executável.");
        }
        emitEntryPoint(asm, entryFunction);
        appendRuntimeSupport(asm);
        asm.append("section .note.GNU-stack noalloc noexec nowrite progbits\n");

        return asm.toString();
    }

    public String generateFunction(IRProgram program, String functionName) {
        List<IRInstruction> target = new ArrayList<>();
        boolean collecting = false;

        for (IRInstruction instruction : program.instructions()) {
            if (isFunctionLabel(instruction)) {
                if (collecting) {
                    break;
                }

                collecting = instruction.destination().value()
                        .equals(functionName);
            }

            if (collecting) {
                target.add(instruction);
            }
        }

        if (target.isEmpty()) {
            throw new IllegalArgumentException(
                    "Função não encontrada na IR: " + functionName);
        }

        StringBuilder asm = new StringBuilder();
        prepareStringLabels(target);
        appendHeader(asm);
        emitFunction(asm, target);
        appendRuntimeSupport(asm);
        asm.append("section .note.GNU-stack noalloc noexec nowrite progbits\n");
        return asm.toString();
    }

    private void appendHeader(StringBuilder asm) {
        asm.append("default rel\n");
        asm.append("section .rodata\n");
        asm.append("__invalid_reference_message: db ")
                .append(utf8Bytes("Erro de execução: tentativa de acessar um objeto nulo ou já liberado.\n"))
                .append("\n");
        asm.append("__invalid_reference_message_length equ $ - __invalid_reference_message - 1\n");
        asm.append("__array_bounds_message: db ")
                .append(utf8Bytes("Erro de execução: índice fora dos limites do array.\n"))
                .append("\n");
        asm.append("__array_bounds_message_length equ $ - __array_bounds_message - 1\n");
        asm.append("__array_size_message: db ")
                .append(utf8Bytes("Erro de execução: tamanho inválido para o array.\n"))
                .append("\n");
        asm.append("__array_size_message_length equ $ - __array_size_message - 1\n");
        asm.append("__invalid_free_message: db ")
                .append(utf8Bytes("Erro de execução: free recebeu um ponteiro inválido ou já liberado.\n"))
                .append("\n");
        asm.append("__invalid_free_message_length equ $ - __invalid_free_message - 1\n");
        if (!stringLabels.isEmpty()) {
            for (Map.Entry<String, String> entry : stringLabels.entrySet()) {
                asm.append(entry.getValue()).append(": db ")
                        .append(utf8Bytes(entry.getKey())).append("\n");
            }
        }
        asm.append("__f64_scale: dq 0x412e848000000000\n");
        asm.append("__f64_round: dq 0x3fe0000000000000\n\n");
        appendVirtualTables(asm);
        asm.append("section .bss\n");
        asm.append("__heap_start: resq 1\n");
        asm.append("__heap_end: resq 1\n");
        asm.append("__number_buffer: resb 32\n\n");
        asm.append("section .text\n\n");
    }

    private void appendVirtualTables(StringBuilder asm) {
        if (virtualMethodTable == null || virtualMethodTable.tables().isEmpty()) {
            return;
        }
        asm.append("section .data\n");
        for (Map.Entry<String, List<String>> table : virtualMethodTable.tables().entrySet()) {
            asm.append("__vtable_").append(table.getKey()).append(":\n");
            if (table.getValue().isEmpty()) {
                asm.append("    dq 0\n");
            } else {
                for (String methodLabel : table.getValue()) {
                    asm.append("    dq ").append(methodLabel).append("\n");
                }
            }
        }
        asm.append('\n');
    }

    private void prepareStringLabels(List<IRInstruction> instructions) {
        stringLabels.clear();
        nextStringLabel = 0;
        registerStringValue("\n");
        registerStringValue("-");
        registerStringValue(".");
        registerStringValue("nan");
        registerStringValue("inf");
        registerStringValue("overflow");
        registerStringValue("(null)");
        for (IRInstruction instruction : instructions) {
            registerString(instruction.destination());
            if (instruction.opCode() == IROpCode.PRINT_FORMAT
                    && instruction.left() != null
                    && instruction.left().kind() == Operand.Kind.CONSTANT
                    && instruction.left().value() instanceof String format) {
                registerFormatLiterals(format);
            } else {
                registerString(instruction.left());
            }
            registerString(instruction.right());
            for (Operand argument : instruction.arguments()) {
                registerString(argument);
            }
        }
    }

    private void registerString(Operand operand) {
        if (operand == null || operand.kind() != Operand.Kind.CONSTANT
                || !(operand.value() instanceof String value)) {
            return;
        }
        registerStringValue(value);
    }

    private void registerStringValue(String value) {
        stringLabels.computeIfAbsent(value, ignored -> "str_data_" + nextStringLabel++);
    }

    private void registerFormatLiterals(String format) {
        StringBuilder literal = new StringBuilder();
        for (int index = 0; index < format.length(); index++) {
            char current = format.charAt(index);
            if (current != '%') {
                literal.append(current);
                continue;
            }
            if (++index >= format.length()) {
                throw new IllegalArgumentException("Especificador de printf incompleto.");
            }
            char specifier = format.charAt(index);
            if (specifier == '%') {
                literal.append('%');
                continue;
            }
            if (!literal.isEmpty()) {
                registerStringValue(literal.toString());
                literal.setLength(0);
            }
            if (specifier == 'l' && index + 1 < format.length()) {
                index++;
            }
        }
        if (!literal.isEmpty()) {
            registerStringValue(literal.toString());
        }
    }

    private String utf8Bytes(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder output = new StringBuilder();
        for (byte valueByte : bytes) {
            if (!output.isEmpty())
                output.append(", ");
            output.append(Byte.toUnsignedInt(valueByte));
        }
        if (!output.isEmpty())
            output.append(", ");
        return output.append('0').toString();
    }

    private boolean isFunctionLabel(IRInstruction instruction) {
        return instruction.opCode() == IROpCode.LABEL
                && instruction.destination() != null
                && instruction.destination().kind() == Operand.Kind.LABEL
                && instruction.destination().value().toString().contains(".");
    }

    private String findEntryFunction(IRProgram program) {
        String stringArrayEntry = null;
        String noArgumentEntry = null;
        for (IRInstruction instruction : program.instructions()) {
            if (!isFunctionLabel(instruction))
                continue;
            String label = instruction.destination().value().toString();
            if (label.endsWith(".main__String_array__ret_void")) {
                if (stringArrayEntry != null) {
                    throw new IllegalStateException("Mais de uma função main(String[]) foi gerada.");
                }
                stringArrayEntry = label;
            } else if (label.endsWith(".main__void__ret_void")) {
                if (noArgumentEntry != null) {
                    throw new IllegalStateException("Mais de uma função main() sem parâmetros foi gerada.");
                }
                noArgumentEntry = label;
            }
        }
        return stringArrayEntry != null ? stringArrayEntry : noArgumentEntry;
    }

    private void emitEntryPoint(StringBuilder asm, String entryFunction) {
        asm.append("global _start\n");
        asm.append("_start:\n");
        asm.append("    xor ebp, ebp\n");
        asm.append("    lea rdi, [rsp+8]\n");
        asm.append("    call ").append(entryFunction).append("\n");
        asm.append("    xor edi, edi\n");
        asm.append("    mov eax, 60\n");
        asm.append("    syscall\n");
    }

    private void emitFunction(
            StringBuilder asm,
            List<IRInstruction> instructions) {
        allocateStackSlots(instructions);

        String name = instructions.get(0).destination().value().toString();
        asm.append(name).append(":\n");
        asm.append("    push rbp\n");
        asm.append("    mov rbp, rsp\n");

        if (frameSize > 0) {
            asm.append("    sub rsp, ").append(frameSize).append("\n");
        }

        nextGpParameter = 0;
        nextXmmParameter = 0;
        for (int i = 1; i < instructions.size(); i++) {
            emitInstruction(asm, instructions.get(i));
        }

        if (instructions.get(instructions.size() - 1).opCode() != IROpCode.RETURN) {
            asm.append("    leave\n");
            asm.append("    ret\n");
        }

        asm.append('\n');
    }

    private void allocateStackSlots(List<IRInstruction> instructions) {
        stackOffsets.clear();
        frameSize = 0;

        for (IRInstruction instruction : instructions) {
            reserve(instruction.destination());
            reserve(instruction.left());
            reserve(instruction.right());

            for (Operand argument : instruction.arguments()) {
                reserve(argument);
            }
        }

        frameSize = align(frameSize, 16);
    }

    private void reserve(Operand operand) {
        if (operand == null
                || (operand.kind() != Operand.Kind.VARIABLE
                        && operand.kind() != Operand.Kind.TEMPORARY)
                || stackOffsets.containsKey(operand)) {
            return;
        }

        int size = switch (operand.type()) {
            case I32, BOOL -> 4;
            case I64, F64, REF -> 8;
        };

        frameSize = align(frameSize, size);
        frameSize += size;
        stackOffsets.put(operand, -frameSize);
    }

    private int align(int value, int alignment) {
        return (value + alignment - 1) / alignment * alignment;
    }

    private String address(Operand operand) {
        Integer offset = stackOffsets.get(operand);
        if (offset == null) {
            throw new IllegalArgumentException(
                    "Operando sem espaço no stack: " + operand);
        }
        return "[rbp" + offset + "]";
    }

    private void emitInstruction(
            StringBuilder asm,
            IRInstruction instruction) {
        switch (instruction.opCode()) {
            case LABEL ->
                asm.append(instruction.destination().value()).append(":\n");

            case PARAM -> {
                Operand destination = instruction.destination();
                if (destination.type() == Operand.Type.F64) {
                    String[] xmmRegisters = {
                            "xmm0", "xmm1", "xmm2", "xmm3",
                            "xmm4", "xmm5", "xmm6", "xmm7"
                    };
                    if (nextXmmParameter >= xmmRegisters.length) {
                        throw new UnsupportedOperationException(
                                "Mais de oito parâmetros double ainda não são suportados.");
                    }
                    asm.append("    movsd qword ").append(address(destination))
                            .append(", ").append(xmmRegisters[nextXmmParameter++]).append("\n");
                } else {
                    String[] gpRegisters = {
                            "rdi", "rsi", "rdx", "rcx", "r8", "r9"
                    };
                    if (nextGpParameter >= gpRegisters.length) {
                        throw new UnsupportedOperationException(
                                "Mais de seis parâmetros inteiros/referência ainda não são suportados.");
                    }
                    String register = gpRegisters[nextGpParameter++];
                    if (destination.type() == Operand.Type.I32
                            || destination.type() == Operand.Type.BOOL) {
                        register = register32(register);
                        asm.append("    mov dword ").append(address(destination))
                                .append(", ").append(register).append("\n");
                    } else if (destination.type() == Operand.Type.I64
                            || destination.type() == Operand.Type.REF) {
                        asm.append("    mov qword ").append(address(destination))
                                .append(", ").append(register).append("\n");
                    } else {
                        throw new UnsupportedOperationException(
                                "Tipo de parâmetro ainda não suportado: " + destination.type());
                    }
                }
            }

            case CALL -> emitCall(asm, instruction);

            case CALL_VIRTUAL -> emitVirtualCall(asm, instruction);

            case LOAD_FIELD -> emitFieldLoad(asm, instruction);

            case STORE_FIELD -> emitFieldStore(asm, instruction);

            case ALLOC_OBJECT -> emitObjectAllocation(asm, instruction);

            case FREE_OBJECT -> emitObjectFree(asm, instruction);

            case ALLOC_ARRAY -> emitArrayAllocation(asm, instruction);

            case LOAD_ARRAY -> emitArrayLoad(asm, instruction);

            case STORE_ARRAY -> emitArrayStore(asm, instruction);

            case ARRAY_LENGTH -> emitArrayLength(asm, instruction);

            case FREE_ARRAY -> emitArrayFree(asm, instruction);

            case CHECK_ARRAY_SIZE -> emitArraySizeCheck(asm, instruction);

            case ASSIGN -> {
                emitAssignment(asm, instruction.destination(), instruction.left());
            }

            case CAST_I32_TO_I64 -> {
                loadI32(asm, instruction.left(), "eax");
                asm.append("    movsxd rax, eax\n");
                storeI64(asm, instruction.destination(), "rax");
            }

            case CAST_I32_TO_F64 -> {
                loadI32(asm, instruction.left(), "eax");
                asm.append("    cvtsi2sd xmm0, eax\n");
                storeF64(asm, instruction.destination(), "xmm0");
            }

            case CAST_I64_TO_F64 -> {
                loadI64(asm, instruction.left(), "rax");
                asm.append("    cvtsi2sd xmm0, rax\n");
                storeF64(asm, instruction.destination(), "xmm0");
            }

            case ADD -> {
                emitIntegerBinary(asm, instruction, "add");
            }

            case SUB, MUL -> emitIntegerBinary(asm, instruction,
                    instruction.opCode() == IROpCode.SUB ? "sub" : "imul");

            case DIV -> emitDivision(asm, instruction, false);

            case MOD -> emitDivision(asm, instruction, true);

            case NEG -> {
                if (instruction.destination().type() == Operand.Type.F64) {
                    loadF64(asm, instruction.left(), "xmm0");
                    asm.append("    mov rax, 0x8000000000000000\n");
                    asm.append("    movq xmm1, rax\n");
                    asm.append("    xorpd xmm0, xmm1\n");
                    storeF64(asm, instruction.destination(), "xmm0");
                } else if (instruction.destination().type() == Operand.Type.I64) {
                    loadI64(asm, instruction.left(), "rax");
                    asm.append("    neg rax\n");
                    storeI64(asm, instruction.destination(), "rax");
                } else {
                    loadI32(asm, instruction.left(), "eax");
                    asm.append("    neg eax\n");
                    storeI32(asm, instruction.destination(), "eax");
                }
            }

            case LT, GT, LE, GE, EQ, NE ->
                emitComparison(asm, instruction);

            case JUMP -> asm.append("    jmp ")
                    .append(instruction.destination().value())
                    .append("\n");

            case JUMP_IF_FALSE -> {
                loadI32(asm, instruction.left(), "eax");
                asm.append("    test eax, eax\n");
                asm.append("    jz ")
                        .append(instruction.destination().value())
                        .append("\n");
            }

            case JUMP_IF_TRUE -> {
                loadI32(asm, instruction.left(), "eax");
                asm.append("    test eax, eax\n");
                asm.append("    jnz ")
                        .append(instruction.destination().value())
                        .append("\n");
            }

            case PRINT -> emitPrint(asm, instruction);

            case PRINT_FORMAT -> emitFormattedPrint(asm, instruction);

            case RETURN -> {
                if (instruction.left() != null) {
                    if (instruction.left().type() == Operand.Type.F64) {
                        loadF64(asm, instruction.left(), "xmm0");
                    } else if (instruction.left().type() == Operand.Type.I64
                            || instruction.left().type() == Operand.Type.REF) {
                        loadI64(asm, instruction.left(), "rax");
                    } else {
                        loadI32(asm, instruction.left(), "eax");
                    }
                }
                asm.append("    leave\n");
                asm.append("    ret\n");
            }

            default -> throw new UnsupportedOperationException(
                    "Opcode NASM ainda não implementado: "
                            + instruction.opCode());
        }
    }

    private void emitPrint(
            StringBuilder asm,
            IRInstruction instruction) {
        Operand value = instruction.left();
        switch (value.type()) {
            case F64 -> {
                loadF64(asm, value, "xmm0");
                asm.append("    call __print_f64\n");
            }
            case REF -> {
                loadI64(asm, value, "rsi");
                asm.append("    call __write_cstr\n");
            }
            case I64 -> {
                loadI64(asm, value, "rax");
                asm.append("    call __print_i64\n");
            }
            case I32 -> {
                loadI32(asm, value, "eax");
                asm.append("    movsxd rax, eax\n");
                asm.append("    call __print_i64\n");
            }
            case BOOL -> {
                loadI32(asm, value, "eax");
                asm.append("    mov eax, eax\n");
                asm.append("    call __print_u64\n");
            }
        }
        emitLiteralWrite(asm, "\n");
    }

    private void emitFormattedPrint(StringBuilder asm, IRInstruction instruction) {
        String format = (String) instruction.left().value();
        List<Operand> arguments = instruction.arguments();
        StringBuilder literal = new StringBuilder();
        int argumentIndex = 0;

        for (int index = 0; index < format.length(); index++) {
            char current = format.charAt(index);
            if (current != '%') {
                literal.append(current);
                continue;
            }

            if (++index >= format.length()) {
                throw new IllegalArgumentException("Especificador de printf incompleto.");
            }

            char specifier = format.charAt(index);
            if (specifier == '%') {
                literal.append('%');
                continue;
            }

            emitLiteralWrite(asm, literal.toString());
            literal.setLength(0);

            boolean longModifier = specifier == 'l';
            if (longModifier) {
                if (++index >= format.length()) {
                    throw new IllegalArgumentException("Especificador de printf incompleto.");
                }
                specifier = format.charAt(index);
            }
            if (argumentIndex >= arguments.size()) {
                throw new IllegalArgumentException("Faltou argumento para o formato de printf.");
            }
            emitFormattedArgument(asm, arguments.get(argumentIndex++), specifier, longModifier);
        }

        emitLiteralWrite(asm, literal.toString());
        if (argumentIndex != arguments.size()) {
            throw new IllegalArgumentException("Há argumentos excedentes no formato de printf.");
        }
    }

    private void emitFormattedArgument(
            StringBuilder asm,
            Operand argument,
            char specifier,
            boolean longModifier) {
        switch (specifier) {
            case 'd', 'i' -> {
                if (longModifier) {
                    loadI64(asm, argument, "rax");
                } else {
                    loadI32(asm, argument, "eax");
                    asm.append("    movsxd rax, eax\n");
                }
                asm.append("    call __print_i64\n");
            }
            case 'u' -> {
                loadI32(asm, argument, "eax");
                asm.append("    mov eax, eax\n");
                asm.append("    call __print_u64\n");
            }
            case 'f' -> {
                loadF64(asm, argument, "xmm0");
                asm.append("    call __print_f64\n");
            }
            case 's' -> {
                loadI64(asm, argument, "rsi");
                asm.append("    call __write_cstr\n");
            }
            case 'b' -> {
                loadI32(asm, argument, "eax");
                asm.append("    mov eax, eax\n");
                asm.append("    call __print_u64\n");
            }
            default -> throw new UnsupportedOperationException(
                    "Especificador de printf não suportado: %" + (longModifier ? "l" : "") + specifier);
        }
    }

    private void emitLiteralWrite(StringBuilder asm, String value) {
        if (value.isEmpty()) {
            return;
        }
        String label = stringLabels.get(value);
        if (label == null) {
            throw new IllegalStateException("Literal não registrado: " + value);
        }
        int byteLength = value.getBytes(StandardCharsets.UTF_8).length;
        asm.append("    lea rsi, [rel ").append(label).append("]\n");
        asm.append("    mov edx, ").append(byteLength).append("\n");
        asm.append("    call __write_all\n");
    }

    private void appendRuntimeSupport(StringBuilder asm) {
        asm.append("__write_all:\n");
        asm.append("    test rdx, rdx\n");
        asm.append("    jz .write_done\n");
        asm.append(".write_loop:\n");
        asm.append("    mov eax, 1\n");
        asm.append("    mov edi, 1\n");
        asm.append("    syscall\n");
        asm.append("    test rax, rax\n");
        asm.append("    jle .write_error\n");
        asm.append("    add rsi, rax\n");
        asm.append("    sub rdx, rax\n");
        asm.append("    jnz .write_loop\n");
        asm.append(".write_done:\n");
        asm.append("    ret\n");
        asm.append(".write_error:\n");
        emitExitSyscall(asm, 1);

        asm.append("__write_cstr:\n");
        asm.append("    test rsi, rsi\n");
        asm.append("    jnz .cstring_scan\n");
        asm.append("    lea rsi, [rel ").append(stringLabels.get("(null)")).append("]\n");
        asm.append("    mov edx, 6\n");
        asm.append("    jmp __write_all\n");
        asm.append(".cstring_scan:\n");
        asm.append("    mov rdx, rsi\n");
        asm.append(".cstring_loop:\n");
        asm.append("    cmp byte [rdx], 0\n");
        asm.append("    je .cstring_ready\n");
        asm.append("    inc rdx\n");
        asm.append("    jmp .cstring_loop\n");
        asm.append(".cstring_ready:\n");
        asm.append("    sub rdx, rsi\n");
        asm.append("    jmp __write_all\n");

        asm.append("__heap_alloc:\n");
        asm.append("    mov r8, rdi\n"); // tamanho solicitado
        asm.append("    add r8, 15\n"); // arredonda o payload
        asm.append("    jc .heap_alloc_failed\n");
        asm.append("    and r8, -16\n"); // alinhamento de 16 bytes
        asm.append("    add r8, 16\n"); // espaço do cabeçalho
        asm.append("    jc .heap_alloc_failed\n");

        asm.append("    mov r9, qword [rel __heap_start]\n");
        asm.append("    test r9, r9\n");
        asm.append("    jnz .heap_search\n");
        asm.append("    xor edi, edi\n"); // consulta o break atual
        asm.append("    mov eax, 12\n"); // syscall brk
        asm.append("    syscall\n");
        asm.append("    add rax, 15\n");
        asm.append("    jc .heap_alloc_failed\n");
        asm.append("    and rax, -16\n");
        asm.append("    mov qword [rel __heap_start], rax\n");
        asm.append("    mov qword [rel __heap_end], rax\n");
        asm.append("    mov r9, rax\n");

        asm.append(".heap_search:\n");
        asm.append("    cmp r9, qword [rel __heap_end]\n");
        asm.append("    jae .heap_grow\n");
        asm.append("    cmp qword [r9 + 8], 0\n");
        asm.append("    jne .heap_next_block\n");
        asm.append("    cmp qword [r9], r8\n");
        asm.append("    jae .heap_reuse_block\n");
        asm.append(".heap_next_block:\n");
        asm.append("    add r9, qword [r9]\n");
        asm.append("    jmp .heap_search\n");

        asm.append(".heap_reuse_block:\n");
        asm.append("    mov r10, qword [r9]\n");
        asm.append("    sub r10, r8\n");
        asm.append("    cmp r10, 32\n"); // cabeçalho + payload mínimo alinhado
        asm.append("    jb .heap_mark_allocated\n");
        asm.append("    lea rax, [r9 + r8]\n");
        asm.append("    mov qword [rax], r10\n");
        asm.append("    mov qword [rax + 8], 0\n");
        asm.append("    mov qword [r9], r8\n");
        asm.append(".heap_mark_allocated:\n");
        asm.append("    mov qword [r9 + 8], 1\n");
        asm.append("    jmp .heap_clear_payload\n");

        asm.append(".heap_grow:\n");
        asm.append("    mov r9, qword [rel __heap_end]\n");
        asm.append("    mov r10, r9\n");
        asm.append("    add r10, r8\n"); // novo break
        asm.append("    jc .heap_alloc_failed\n");
        asm.append("    mov rdi, r10\n");
        asm.append("    mov eax, 12\n");
        asm.append("    syscall\n");
        asm.append("    cmp rax, r10\n");
        asm.append("    jb .heap_alloc_failed\n");
        asm.append("    mov qword [rel __heap_end], r10\n");
        asm.append("    mov qword [r9], r8\n");
        asm.append("    mov qword [r9 + 8], 1\n");

        asm.append(".heap_clear_payload:\n");
        asm.append("    mov qword [r9 + 8], 1\n");
        asm.append("    lea rdi, [r9 + 16]\n");
        asm.append("    mov rcx, r8\n");
        asm.append("    shr rcx, 3\n");
        asm.append("    sub rcx, 2\n");
        asm.append("    xor eax, eax\n");
        asm.append("    cld\n");
        asm.append("    rep stosq\n");
        asm.append("    lea rax, [r9 + 16]\n");
        asm.append("    ret\n");

        asm.append(".heap_alloc_failed:\n");
        asm.append("    mov edi, 1\n");
        asm.append("    mov eax, 60\n"); // exit
        asm.append("    syscall\n");
        asm.append("    ud2\n");

        asm.append("__heap_free:\n");
        asm.append("    test rdi, rdi\n");
        asm.append("    jz .heap_free_done\n");
        asm.append("    mov r11, rdi\n"); // preserva o ponteiro entregue
        asm.append("    mov r8, qword [rel __heap_start]\n");
        asm.append("    test r8, r8\n");
        asm.append("    jz .heap_invalid_free\n");
        asm.append(".heap_free_search:\n");
        asm.append("    cmp r8, qword [rel __heap_end]\n");
        asm.append("    jae .heap_invalid_free\n");
        asm.append("    lea rax, [r8 + 16]\n");
        asm.append("    cmp rax, r11\n");
        asm.append("    je .heap_free_found\n");
        asm.append("    mov rax, qword [r8]\n");
        asm.append("    test rax, rax\n");
        asm.append("    jz .heap_invalid_free\n");
        asm.append("    add r8, rax\n");
        asm.append("    jmp .heap_free_search\n");
        asm.append(".heap_free_found:\n");
        asm.append("    cmp qword [r8 + 8], 1\n");
        asm.append("    jne .heap_invalid_free\n"); // double-free
        asm.append("    mov qword [r8 + 8], 0\n");
        asm.append("    mov r9, qword [r8]\n");
        asm.append("    lea r10, [r8 + r9]\n"); // combina com o próximo bloco livre
        asm.append("    cmp r10, qword [rel __heap_end]\n");
        asm.append("    jae .heap_try_previous\n");
        asm.append("    cmp qword [r10 + 8], 0\n");
        asm.append("    jne .heap_try_previous\n");
        asm.append("    add r9, qword [r10]\n");
        asm.append("    mov qword [r8], r9\n");
        asm.append(".heap_try_previous:\n");
        asm.append("    mov r10, qword [rel __heap_start]\n");
        asm.append("    xor edx, edx\n"); // bloco anterior
        asm.append(".heap_previous_search:\n");
        asm.append("    cmp r10, r8\n");
        asm.append("    jae .heap_free_done\n");
        asm.append("    mov rdx, r10\n");
        asm.append("    add r10, qword [r10]\n");
        asm.append("    jmp .heap_previous_search\n");
        asm.append(".heap_free_done:\n");
        asm.append("    test rdx, rdx\n");
        asm.append("    jz .heap_free_return\n");
        asm.append("    cmp qword [rdx + 8], 0\n");
        asm.append("    jne .heap_free_return\n");
        asm.append("    mov rax, qword [r8]\n");
        asm.append("    add qword [rdx], rax\n");
        asm.append(".heap_free_return:\n");
        asm.append("    ret\n");
        asm.append(".heap_invalid_free:\n");
        asm.append("    mov eax, 1\n");
        asm.append("    mov edi, 2\n");
        asm.append("    lea rsi, [rel __invalid_free_message]\n");
        asm.append("    mov edx, __invalid_free_message_length\n");
        asm.append("    syscall\n");
        asm.append("    mov eax, 60\n");
        asm.append("    mov edi, 1\n");
        asm.append("    syscall\n");
        asm.append("    ud2\n");

        asm.append("__heap_validate:\n");
        asm.append("    mov r11, rdi\n");
        asm.append("    mov r8, qword [rel __heap_start]\n");
        asm.append("    test r8, r8\n");
        asm.append("    jz __runtime_invalid_reference\n");
        asm.append(".heap_validate_search:\n");
        asm.append("    cmp r8, qword [rel __heap_end]\n");
        asm.append("    jae __runtime_invalid_reference\n");
        asm.append("    lea rax, [r8 + 16]\n");
        asm.append("    cmp rax, r11\n");
        asm.append("    je .heap_validate_found\n");
        asm.append("    mov rax, qword [r8]\n");
        asm.append("    test rax, rax\n");
        asm.append("    jz __runtime_invalid_reference\n");
        asm.append("    add r8, rax\n");
        asm.append("    jmp .heap_validate_search\n");
        asm.append(".heap_validate_found:\n");
        asm.append("    cmp qword [r8 + 8], 1\n");
        asm.append("    jne __runtime_invalid_reference\n");
        asm.append("    ret\n");

        asm.append("__runtime_invalid_reference:\n");
        asm.append("    mov eax, 1\n");
        asm.append("    mov edi, 2\n");
        asm.append("    lea rsi, [rel __invalid_reference_message]\n");
        asm.append("    mov edx, __invalid_reference_message_length\n");
        asm.append("    syscall\n");
        asm.append("    mov eax, 60\n");
        asm.append("    mov edi, 1\n");
        asm.append("    syscall\n");
        asm.append("    ud2\n");

        asm.append("__runtime_array_bounds:\n");
        asm.append("    mov eax, 1\n");
        asm.append("    mov edi, 2\n");
        asm.append("    lea rsi, [rel __array_bounds_message]\n");
        asm.append("    mov edx, __array_bounds_message_length\n");
        asm.append("    syscall\n");
        asm.append("    mov eax, 60\n");
        asm.append("    mov edi, 1\n");
        asm.append("    syscall\n");
        asm.append("    ud2\n");

        asm.append("__runtime_invalid_array_size:\n");
        asm.append("    mov eax, 1\n");
        asm.append("    mov edi, 2\n");
        asm.append("    lea rsi, [rel __array_size_message]\n");
        asm.append("    mov edx, __array_size_message_length\n");
        asm.append("    syscall\n");
        asm.append("    mov eax, 60\n");
        asm.append("    mov edi, 1\n");
        asm.append("    syscall\n");
        asm.append("    ud2\n");

        asm.append("__print_u64:\n");
        asm.append("    lea r8, [rel __number_buffer + 32]\n");
        asm.append("    mov r9d, 10\n");
        asm.append(".u64_loop:\n");
        asm.append("    xor edx, edx\n");
        asm.append("    div r9\n");
        asm.append("    dec r8\n");
        asm.append("    add dl, '0'\n");
        asm.append("    mov byte [r8], dl\n");
        asm.append("    test rax, rax\n");
        asm.append("    jnz .u64_loop\n");
        asm.append("    mov rsi, r8\n");
        asm.append("    lea rdx, [rel __number_buffer + 32]\n");
        asm.append("    sub rdx, r8\n");
        asm.append("    jmp __write_all\n");

        asm.append("__print_i64:\n");
        asm.append("    test rax, rax\n");
        asm.append("    jns .i64_positive\n");
        asm.append("    mov r10, rax\n");
        asm.append("    neg r10\n");
        asm.append("    lea rsi, [rel ").append(stringLabels.get("-")).append("]\n");
        asm.append("    mov edx, 1\n");
        asm.append("    call __write_all\n");
        asm.append("    mov rax, r10\n");
        asm.append("    jmp __print_u64\n");
        asm.append(".i64_positive:\n");
        asm.append("    jmp __print_u64\n");

        appendFloatPrinter(asm);
    }

    private void appendFloatPrinter(StringBuilder asm) {
        String minusLabel = stringLabels.get("-");
        String periodLabel = stringLabels.get(".");
        String nanLabel = stringLabels.get("nan");
        String infinityLabel = stringLabels.get("inf");
        String overflowLabel = stringLabels.get("overflow");

        asm.append("__print_f64:\n");
        asm.append("    movq rax, xmm0\n");
        asm.append("    mov rcx, 0x8000000000000000\n");
        asm.append("    test rax, rcx\n");
        asm.append("    jz .f64_abs_ready\n");
        asm.append("    shl rax, 1\n");
        asm.append("    shr rax, 1\n");
        asm.append("    movq xmm0, rax\n");
        asm.append("    lea rsi, [rel ").append(minusLabel).append("]\n");
        asm.append("    mov edx, 1\n");
        asm.append("    call __write_all\n");
        asm.append(".f64_abs_ready:\n");
        asm.append("    movq rax, xmm0\n");
        asm.append("    mov rcx, 0x7ff0000000000000\n");
        asm.append("    mov rdx, rax\n");
        asm.append("    and rdx, rcx\n");
        asm.append("    cmp rdx, rcx\n");
        asm.append("    jne .f64_finite\n");
        asm.append("    mov rdx, 0x000fffffffffffff\n");
        asm.append("    test rax, rdx\n");
        asm.append("    jnz .f64_nan\n");
        asm.append("    lea rsi, [rel ").append(infinityLabel).append("]\n");
        asm.append("    mov edx, 3\n");
        asm.append("    jmp __write_all\n");
        asm.append(".f64_nan:\n");
        asm.append("    lea rsi, [rel ").append(nanLabel).append("]\n");
        asm.append("    mov edx, 3\n");
        asm.append("    jmp __write_all\n");
        asm.append(".f64_finite:\n");
        asm.append("    mulsd xmm0, qword [rel __f64_scale]\n");
        asm.append("    addsd xmm0, qword [rel __f64_round]\n");
        asm.append("    cvttsd2si rax, xmm0\n");
        asm.append("    mov rcx, 0x7fffffffffffffff\n");
        asm.append("    cmp rax, rcx\n");
        asm.append("    ja .f64_overflow\n");
        asm.append("    mov ecx, 1000000\n");
        asm.append("    xor edx, edx\n");
        asm.append("    div rcx\n");
        asm.append("    mov r10, rdx\n");
        asm.append("    call __print_u64\n");
        asm.append("    lea rsi, [rel ").append(periodLabel).append("]\n");
        asm.append("    mov edx, 1\n");
        asm.append("    call __write_all\n");
        asm.append("    mov rax, r10\n");
        asm.append("    lea r8, [rel __number_buffer + 6]\n");
        asm.append("    mov r9d, 10\n");
        asm.append("    mov ecx, 6\n");
        asm.append(".f64_fraction_loop:\n");
        asm.append("    xor edx, edx\n");
        asm.append("    div r9\n");
        asm.append("    dec r8\n");
        asm.append("    add dl, '0'\n");
        asm.append("    mov byte [r8], dl\n");
        asm.append("    loop .f64_fraction_loop\n");
        asm.append("    mov rsi, r8\n");
        asm.append("    mov edx, 6\n");
        asm.append("    jmp __write_all\n");
        asm.append(".f64_overflow:\n");
        asm.append("    lea rsi, [rel ").append(overflowLabel).append("]\n");
        asm.append("    mov edx, 8\n");
        asm.append("    jmp __write_all\n");
    }

    private void emitExitSyscall(StringBuilder asm, int status) {
        asm.append("    mov edi, ").append(status).append("\n");
        asm.append("    mov eax, 60\n");
        asm.append("    syscall\n");
        asm.append("    ud2\n");
    }

    private void emitObjectAllocation(StringBuilder asm, IRInstruction instruction) {
        if (instruction.destination() == null
                || instruction.destination().type() != Operand.Type.REF
                || instruction.left() == null
                || instruction.right() == null
                || instruction.right().kind() != Operand.Kind.CONSTANT
                || !(instruction.right().value() instanceof String className)) {
            throw new IllegalArgumentException(
                    "ALLOC_OBJECT exige destino REF, tamanho e nome da classe.");
        }

        loadI32(asm, instruction.left(), "edi");
        asm.append("    call __heap_alloc\n");
        asm.append("    lea rdx, [rel __vtable_").append(className).append(
                "]\n");
        asm.append("    mov qword [rax], rdx\n");
        storeI64(asm, instruction.destination(), "rax");
    }

    private void emitObjectFree(StringBuilder asm, IRInstruction instruction) {
        if (instruction.left() == null || instruction.left().type() != Operand.Type.REF) {
            throw new IllegalArgumentException("FREE_OBJECT exige uma referência de objeto.");
        }
        int labelId = nextFreeLabel++;
        String doneLabel = ".L_free_done_" + labelId;

        loadI64(asm, instruction.left(), "rdi");
        asm.append("    test rdi, rdi\n");
        asm.append("    jz ").append(doneLabel).append("\n");
        asm.append("    call __heap_free\n");
        if (instruction.left().kind() == Operand.Kind.VARIABLE
                || instruction.left().kind() == Operand.Kind.TEMPORARY) {
            asm.append("    xor eax, eax\n");
            storeI64(asm, instruction.left(), "rax");
        }
        asm.append(doneLabel).append(":\n");
    }

    private void emitArrayAllocation(StringBuilder asm, IRInstruction instruction) {
        if (instruction.destination() == null
                || instruction.destination().type() != Operand.Type.REF
                || instruction.left() == null
                || instruction.right() == null
                || instruction.right().kind() != Operand.Kind.CONSTANT
                || !(instruction.right().value() instanceof Integer elementSize)
                || (elementSize != 1 && elementSize != 4 && elementSize != 8)) {
            throw new IllegalArgumentException("ALLOC_ARRAY exige destino, tamanho e tipo de elemento válidos.");
        }

        loadI32(asm, instruction.left(), "eax");
        asm.append("    test eax, eax\n");
        asm.append("    js __runtime_invalid_array_size\n");
        asm.append("    movsxd rdi, eax\n");
        asm.append("    imul rdi, ").append(elementSize).append("\n");
        asm.append("    add rdi, 8\n"); // espaço para o comprimento
        asm.append("    jc __runtime_invalid_array_size\n");
        asm.append("    call __heap_alloc\n");
        loadI32(asm, instruction.left(), "ecx");
        asm.append("    mov qword [rax], rcx\n"); // comprimento no início do array
        storeI64(asm, instruction.destination(), "rax");
    }

    private void emitArrayFree(StringBuilder asm, IRInstruction instruction) {
        if (instruction.left() == null || instruction.left().type() != Operand.Type.REF) {
            throw new IllegalArgumentException("FREE_ARRAY exige uma referência de array.");
        }
        int labelId = nextFreeLabel++;
        String doneLabel = ".L_array_free_done_" + labelId;
        loadI64(asm, instruction.left(), "rdi");
        asm.append("    test rdi, rdi\n");
        asm.append("    jz ").append(doneLabel).append("\n");
        asm.append("    call __heap_free\n");
        if (instruction.left().kind() == Operand.Kind.VARIABLE
                || instruction.left().kind() == Operand.Kind.TEMPORARY) {
            asm.append("    xor eax, eax\n");
            storeI64(asm, instruction.left(), "rax");
        }
        asm.append(doneLabel).append(":\n");
    }

    private void emitArrayLength(StringBuilder asm, IRInstruction instruction) {
        if (instruction.destination() == null || instruction.left() == null) {
            throw new IllegalArgumentException("ARRAY_LENGTH exige destino e array.");
        }
        loadI64(asm, instruction.left(), "r11");
        asm.append("    test r11, r11\n");
        asm.append("    jz __runtime_invalid_reference\n");
        asm.append("    mov rdi, r11\n");
        asm.append("    call __heap_validate\n");
        asm.append("    mov eax, dword [r11]\n");
        storeI32(asm, instruction.destination(), "eax");
    }

    private void emitArrayLoad(StringBuilder asm, IRInstruction instruction) {
        if (instruction.destination() == null
                || instruction.left() == null
                || instruction.right() == null) {
            throw new IllegalArgumentException("LOAD_ARRAY exige destino, array e índice.");
        }
        int elementSize = elementSize(instruction.destination().type());
        emitArrayElementAddress(asm, instruction.left(), instruction.right(), elementSize);
        switch (instruction.destination().type()) {
            case I32 -> {
                asm.append("    mov eax, dword [r11 + r10]\n");
                storeI32(asm, instruction.destination(), "eax");
            }
            case BOOL -> {
                asm.append("    movzx eax, byte [r11 + r10]\n");
                storeI32(asm, instruction.destination(), "eax");
            }
            case I64, REF -> {
                asm.append("    mov rax, qword [r11 + r10]\n");
                storeI64(asm, instruction.destination(), "rax");
            }
            case F64 -> {
                asm.append("    movsd xmm0, qword [r11 + r10]\n");
                storeF64(asm, instruction.destination(), "xmm0");
            }
        }
    }

    private void emitArrayStore(StringBuilder asm, IRInstruction instruction) {
        if (instruction.left() == null
                || instruction.right() == null
                || instruction.arguments().size() != 1) {
            throw new IllegalArgumentException("STORE_ARRAY exige array, índice e valor.");
        }
        Operand value = instruction.arguments().get(0);
        int elementSize = elementSize(value.type());
        emitArrayElementAddress(asm, instruction.left(), instruction.right(), elementSize);
        switch (value.type()) {
            case I32 -> {
                loadI32(asm, value, "edx");
                asm.append("    mov dword [r11 + r10], edx\n");
            }
            case BOOL -> {
                loadI32(asm, value, "edx");
                asm.append("    mov byte [r11 + r10], dl\n");
            }
            case I64, REF -> {
                loadI64(asm, value, "rdx");
                asm.append("    mov qword [r11 + r10], rdx\n");
            }
            case F64 -> {
                loadF64(asm, value, "xmm0");
                asm.append("    movsd qword [r11 + r10], xmm0\n");
            }
        }
    }

    private int elementSize(Operand.Type type) {
        return switch (type) {
            case BOOL -> 1;
            case I32 -> 4;
            case I64, F64, REF -> 8;
        };
    }

    private void emitArraySizeCheck(StringBuilder asm, IRInstruction instruction) {
        if (instruction.left() == null || instruction.left().type() != Operand.Type.I32) {
            throw new IllegalArgumentException("CHECK_ARRAY_SIZE exige um tamanho int.");
        }
        loadI32(asm, instruction.left(), "eax");
        asm.append("    test eax, eax\n");
        asm.append("    js __runtime_invalid_array_size\n");
    }

    private void emitArrayElementAddress(
            StringBuilder asm,
            Operand array,
            Operand index,
            int elementSize) {
        loadI64(asm, array, "r11");
        asm.append("    test r11, r11\n");
        asm.append("    jz __runtime_invalid_reference\n");
        asm.append("    mov rdi, r11\n");
        asm.append("    call __heap_validate\n");
        loadI32(asm, index, "eax");
        asm.append("    test eax, eax\n");
        asm.append("    js __runtime_array_bounds\n");
        asm.append("    movsxd r10, eax\n");
        asm.append("    cmp r10, qword [r11]\n");
        asm.append("    jae __runtime_array_bounds\n");
        if (elementSize != 1) {
            asm.append("    imul r10, ").append(elementSize).append("\n");
        }
        asm.append("    add r11, 8\n");
    }

    private void emitFieldStore(StringBuilder asm, IRInstruction instruction) {
        Operand receiver = instruction.left();
        Operand offsetOperand = instruction.right();

        if (receiver == null
                || offsetOperand == null
                || offsetOperand.kind() != Operand.Kind.CONSTANT
                || !(offsetOperand.value() instanceof Integer offset)
                || instruction.arguments().size() != 1) {
            throw new IllegalArgumentException(
                    "STORE_FIELD exige receptor, offset inteiro e um valor.");
        }

        Operand value = instruction.arguments().get(0);

        loadI64(asm, receiver, "r11");
        asm.append("    test r11, r11\n");
        asm.append("    jz __runtime_invalid_reference\n");
        asm.append("    mov rdi, r11\n");
        asm.append("    call __heap_validate\n");

        String fieldAddress = offset == 0
                ? "[r11]"
                : "[r11 + " + offset + "]";

        switch (value.type()) {
            case I32, BOOL -> {
                loadI32(asm, value, "edx");
                asm.append("    mov dword ")
                        .append(fieldAddress)
                        .append(", edx\n");
            }
            case I64, REF -> {
                loadI64(asm, value, "rdx");
                asm.append("    mov qword ")
                        .append(fieldAddress)
                        .append(", rdx\n");
            }
            case F64 -> {
                loadF64(asm, value, "xmm0");
                asm.append("    movsd qword ")
                        .append(fieldAddress)
                        .append(", xmm0\n");
            }
            default -> throw new UnsupportedOperationException(
                    "Tipo não suportado em STORE_FIELD: " + value.type());
        }
    }

    private void emitFieldLoad(StringBuilder asm, IRInstruction instruction) {
        Operand base = instruction.left();
        Operand offsetOperand = instruction.right();

        if (base == null
                || offsetOperand == null
                || offsetOperand.kind() != Operand.Kind.CONSTANT
                || !(offsetOperand.value() instanceof Integer offset)) {
            throw new IllegalArgumentException(
                    "LOAD_FIELD exige uma referência e um offset inteiro.");
        }

        loadI64(asm, base, "rax");
        asm.append("    test rax, rax\n");
        asm.append("    jz __runtime_invalid_reference\n");
        asm.append("    mov rdi, rax\n");
        asm.append("    push rax\n");
        asm.append("    call __heap_validate\n");
        asm.append("    pop rax\n");

        String fieldAddress = offset == 0
                ? "[rax]"
                : "[rax + " + offset + "]";

        switch (instruction.destination().type()) {
            case I32, BOOL -> {
                asm.append("    mov eax, dword ")
                        .append(fieldAddress)
                        .append("\n");
                storeI32(asm, instruction.destination(), "eax");
            }
            case I64, REF -> {
                asm.append("    mov rdx, qword ")
                        .append(fieldAddress)
                        .append("\n");
                storeI64(asm, instruction.destination(), "rdx");
            }
            case F64 -> {
                asm.append("    movsd xmm0, qword ")
                        .append(fieldAddress)
                        .append("\n");
                storeF64(asm, instruction.destination(), "xmm0");
            }
            default -> throw new UnsupportedOperationException(
                    "Tipo não suportado em LOAD_FIELD: "
                            + instruction.destination().type());
        }
    }

    private void emitIntegerBinary(
            StringBuilder asm,
            IRInstruction instruction,
            String opcode) {
        if (instruction.destination().type() == Operand.Type.F64) {
            loadF64(asm, instruction.left(), "xmm0");
            loadF64(asm, instruction.right(), "xmm1");
            String floatingOpcode = switch (instruction.opCode()) {
                case ADD -> "addsd";
                case SUB -> "subsd";
                case MUL -> "mulsd";
                default -> throw new UnsupportedOperationException(
                        "Operação double não implementada: " + instruction.opCode());
            };
            asm.append("    ").append(floatingOpcode).append(" xmm0, xmm1\n");
            storeF64(asm, instruction.destination(), "xmm0");
        } else if (instruction.destination().type() == Operand.Type.I64) {
            loadI64(asm, instruction.left(), "rax");
            loadI64(asm, instruction.right(), "rcx");
            asm.append("    ").append(opcode).append(" rax, rcx\n");
            storeI64(asm, instruction.destination(), "rax");
        } else {
            loadI32(asm, instruction.left(), "eax");
            loadI32(asm, instruction.right(), "ecx");
            asm.append("    ").append(opcode).append(" eax, ecx\n");
            storeI32(asm, instruction.destination(), "eax");
        }
    }

    private void emitDivision(
            StringBuilder asm,
            IRInstruction instruction,
            boolean remainder) {
        if (instruction.destination().type() == Operand.Type.F64) {
            if (remainder) {
                throw new UnsupportedOperationException("mod double ainda não é suportado.");
            }
            loadF64(asm, instruction.left(), "xmm0");
            loadF64(asm, instruction.right(), "xmm1");
            asm.append("    divsd xmm0, xmm1\n");
            storeF64(asm, instruction.destination(), "xmm0");
            return;
        }
        int id = nextDivisionLabel++;
        String normal = ".L_div_normal_" + id;
        String done = ".L_div_done_" + id;
        if (instruction.destination().type() == Operand.Type.I64) {
            loadI64(asm, instruction.left(), "rax");
            loadI64(asm, instruction.right(), "rcx");
            asm.append("    cmp rcx, -1\n");
            asm.append("    jne ").append(normal).append("\n");
            asm.append("    mov rdx, -9223372036854775808\n");
            asm.append("    cmp rax, rdx\n");
            asm.append("    jne ").append(normal).append("\n");
            if (remainder)
                asm.append("    xor edx, edx\n");
            asm.append("    jmp ").append(done).append("\n");
            asm.append(normal).append(":\n");
            asm.append("    cqo\n");
            asm.append("    idiv rcx\n");
            asm.append(done).append(":\n");
            storeI64(asm, instruction.destination(), remainder ? "rdx" : "rax");
        } else {
            loadI32(asm, instruction.left(), "eax");
            loadI32(asm, instruction.right(), "ecx");
            asm.append("    cmp ecx, -1\n");
            asm.append("    jne ").append(normal).append("\n");
            asm.append("    cmp eax, -2147483648\n");
            asm.append("    jne ").append(normal).append("\n");
            if (remainder)
                asm.append("    xor edx, edx\n");
            asm.append("    jmp ").append(done).append("\n");
            asm.append(normal).append(":\n");
            asm.append("    cdq\n");
            asm.append("    idiv ecx\n");
            asm.append(done).append(":\n");
            storeI32(asm, instruction.destination(), remainder ? "edx" : "eax");
        }
    }

    private void emitComparison(
            StringBuilder asm,
            IRInstruction instruction) {
        if (instruction.left().type() == Operand.Type.F64) {
            loadF64(asm, instruction.left(), "xmm0");
            loadF64(asm, instruction.right(), "xmm1");
            asm.append("    ucomisd xmm0, xmm1\n");
            switch (instruction.opCode()) {
                case EQ -> {
                    asm.append("    setnp al\n    sete cl\n    and al, cl\n");
                }
                case NE -> {
                    asm.append("    setp al\n    setne cl\n    or al, cl\n");
                }
                case LT -> {
                    asm.append("    setb al\n    setnp cl\n    and al, cl\n");
                }
                case LE -> {
                    asm.append("    setbe al\n    setnp cl\n    and al, cl\n");
                }
                case GT -> asm.append("    seta al\n");
                case GE -> {
                    asm.append("    setae al\n    setnp cl\n    and al, cl\n");
                }
                default -> throw new IllegalArgumentException(
                        "Não é uma comparação: " + instruction.opCode());
            }
            asm.append("    movzx eax, al\n");
            storeI32(asm, instruction.destination(), "eax");
            return;
        }
        boolean wide = instruction.left().type() == Operand.Type.I64
                || instruction.left().type() == Operand.Type.REF;
        if (wide) {
            loadI64(asm, instruction.left(), "rax");
            loadI64(asm, instruction.right(), "rcx");
            asm.append("    cmp rax, rcx\n");
        } else {
            loadI32(asm, instruction.left(), "eax");
            loadI32(asm, instruction.right(), "ecx");
            asm.append("    cmp eax, ecx\n");
        }

        String setInstruction = switch (instruction.opCode()) {
            case LT -> "setl";
            case GT -> "setg";
            case LE -> "setle";
            case GE -> "setge";
            case EQ -> "sete";
            case NE -> "setne";
            default -> throw new IllegalArgumentException(
                    "Não é uma comparação: " + instruction.opCode());
        };

        asm.append("    ").append(setInstruction).append(" al\n");
        asm.append("    movzx eax, al\n");
        storeI32(asm, instruction.destination(), "eax");
    }

    private void emitCall(
            StringBuilder asm,
            IRInstruction instruction) {
        String[] gpRegisters = { "rdi", "rsi", "rdx", "rcx", "r8", "r9" };
        String[] xmmRegisters = {
                "xmm0", "xmm1", "xmm2", "xmm3",
                "xmm4", "xmm5", "xmm6", "xmm7"
        };
        int gpIndex = 0;
        int xmmIndex = 0;
        for (Operand argument : instruction.arguments()) {
            if (argument.type() == Operand.Type.F64) {
                if (xmmIndex == xmmRegisters.length) {
                    throw new UnsupportedOperationException(
                            "Chamadas suportam até oito argumentos double em registradores.");
                }
                loadF64(asm, argument, xmmRegisters[xmmIndex++]);
            } else {
                if (gpIndex == gpRegisters.length) {
                    throw new UnsupportedOperationException(
                            "Chamadas suportam até seis argumentos inteiros/referência em registradores.");
                }
                String register = gpRegisters[gpIndex++];
                if (argument.type() == Operand.Type.I32 || argument.type() == Operand.Type.BOOL) {
                    loadI32(asm, argument, register32(register));
                } else {
                    loadI64(asm, argument, register);
                }
            }
        }

        asm.append("    call ")
                .append(instruction.left().value())
                .append("\n");

        if (instruction.destination() != null) {
            if (instruction.destination().type() == Operand.Type.F64) {
                storeF64(asm, instruction.destination(), "xmm0");
            } else if (instruction.destination().type() == Operand.Type.I64
                    || instruction.destination().type() == Operand.Type.REF) {
                storeI64(asm, instruction.destination(), "rax");
            } else {
                storeI32(asm, instruction.destination(), "eax");
            }
        }
    }

    private void emitVirtualCall(StringBuilder asm, IRInstruction instruction) {
        if (virtualMethodTable == null
                || instruction.left() == null
                || instruction.right() == null
                || instruction.right().kind() != Operand.Kind.CONSTANT
                || !(instruction.right().value() instanceof Integer slot)
                || slot < 0) {
            throw new IllegalArgumentException("CALL_VIRTUAL exige receptor e slot válido.");
        }

        loadI64(asm, instruction.left(), "rdi");
        asm.append("    test rdi, rdi\n");
        asm.append("    jz __runtime_invalid_reference\n");
        asm.append("    call __heap_validate\n");
        asm.append("    mov rax, qword [rdi]\n");
        asm.append("    mov r10, qword [rax + ").append(slot * 8).append("]\n");
        asm.append("    test r10, r10\n");
        asm.append("    jz __runtime_invalid_reference\n");

        String[] gpRegisters = { "rsi", "rdx", "rcx", "r8", "r9" };
        String[] xmmRegisters = {
                "xmm0", "xmm1", "xmm2", "xmm3",
                "xmm4", "xmm5", "xmm6", "xmm7"
        };
        int gpIndex = 0;
        int xmmIndex = 0;
        for (Operand argument : instruction.arguments()) {
            if (argument.type() == Operand.Type.F64) {
                if (xmmIndex == xmmRegisters.length) {
                    throw new UnsupportedOperationException(
                            "Chamadas virtuais suportam até oito argumentos double.");
                }
                loadF64(asm, argument, xmmRegisters[xmmIndex++]);
            } else {
                if (gpIndex == gpRegisters.length) {
                    throw new UnsupportedOperationException(
                            "Chamadas virtuais suportam até cinco argumentos inteiros/referência além de this.");
                }
                String register = gpRegisters[gpIndex++];
                if (argument.type() == Operand.Type.I32 || argument.type() == Operand.Type.BOOL) {
                    loadI32(asm, argument, register32(register));
                } else {
                    loadI64(asm, argument, register);
                }
            }
        }

        asm.append("    call r10\n");
        storeCallResult(asm, instruction.destination());
    }

    private void storeCallResult(StringBuilder asm, Operand destination) {
        if (destination == null) {
            return;
        }
        if (destination.type() == Operand.Type.F64) {
            storeF64(asm, destination, "xmm0");
        } else if (destination.type() == Operand.Type.I64 || destination.type() == Operand.Type.REF) {
            storeI64(asm, destination, "rax");
        } else {
            storeI32(asm, destination, "eax");
        }
    }

    private void loadI32(
            StringBuilder asm,
            Operand source,
            String register) {
        if (source.kind() == Operand.Kind.CONSTANT) {
            String immediate;

            if (source.value() instanceof Boolean boolValue) {
                immediate = boolValue ? "1" : "0";
            } else {
                immediate = source.value().toString();
            }

            asm.append("    mov ")
                    .append(register)
                    .append(", ")
                    .append(immediate)
                    .append("\n");
        } else {
            asm.append("    mov ")
                    .append(register)
                    .append(", dword ")
                    .append(address(source))
                    .append("\n");
        }
    }

    private void emitAssignment(StringBuilder asm, Operand destination, Operand source) {
        if (destination.type() == Operand.Type.I32 || destination.type() == Operand.Type.BOOL) {
            loadI32(asm, source, "eax");
            storeI32(asm, destination, "eax");
        } else if (destination.type() == Operand.Type.F64) {
            loadF64(asm, source, "xmm0");
            storeF64(asm, destination, "xmm0");
        } else if (destination.type() == Operand.Type.I64 || destination.type() == Operand.Type.REF) {
            loadI64(asm, source, "rax");
            storeI64(asm, destination, "rax");
        } else {
            throw new UnsupportedOperationException(
                    "Atribuição não suportada para o tipo " + destination.type() + ".");
        }
    }

    private void loadI64(StringBuilder asm, Operand source, String register) {
        if (source.kind() == Operand.Kind.CONSTANT) {
            Object value = source.value();
            if (value instanceof String text) {
                asm.append("    lea ").append(register).append(", [rel ")
                        .append(stringLabels.get(text)).append("]\n");
                return;
            }
            String immediate;
            if (value == null)
                immediate = "0";
            else if (value instanceof Boolean booleanValue)
                immediate = booleanValue ? "1" : "0";
            else
                immediate = value.toString();
            asm.append("    mov ").append(register).append(", ").append(immediate).append("\n");
            return;
        }
        if (source.type() == Operand.Type.I32 || source.type() == Operand.Type.BOOL) {
            if (register32(register) == null) {
                throw new IllegalArgumentException("Registrador de 64 bits inválido: " + register);
            }
            asm.append("    movsxd ").append(register).append(", dword ")
                    .append(address(source)).append("\n");
            return;
        }
        asm.append("    mov ").append(register).append(", qword ")
                .append(address(source)).append("\n");
    }

    private void loadF64(StringBuilder asm, Operand source, String register) {
        if (source.kind() == Operand.Kind.CONSTANT) {
            if (!(source.value() instanceof Double value)) {
                throw new IllegalArgumentException("Constante F64 inválida: " + source.value());
            }
            String bits = Long.toUnsignedString(Double.doubleToRawLongBits(value), 16);
            asm.append("    mov rax, 0x").append(bits).append("\n");
            asm.append("    movq ").append(register).append(", rax\n");
            return;
        }
        asm.append("    movsd ").append(register).append(", qword ")
                .append(address(source)).append("\n");
    }

    private void storeF64(StringBuilder asm, Operand destination, String register) {
        asm.append("    movsd qword ").append(address(destination))
                .append(", ").append(register).append("\n");
    }

    private String register32(String register64) {
        return switch (register64) {
            case "rax" -> "eax";
            case "rdi" -> "edi";
            case "rsi" -> "esi";
            case "rdx" -> "edx";
            case "rcx" -> "ecx";
            case "r8" -> "r8d";
            case "r9" -> "r9d";
            default -> null;
        };
    }

    private void storeI64(StringBuilder asm, Operand destination, String register) {
        asm.append("    mov qword ").append(address(destination))
                .append(", ").append(register).append("\n");
    }

    private void storeI32(
            StringBuilder asm,
            Operand destination,
            String register) {
        asm.append("    mov dword ")
                .append(address(destination))
                .append(", ")
                .append(register)
                .append("\n");
    }
}
