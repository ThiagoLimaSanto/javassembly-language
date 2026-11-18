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

public class NasmGenerator {

    private final Map<Operand, Integer> stackOffsets = new LinkedHashMap<>();
    private final Map<String, String> stringLabels = new LinkedHashMap<>();
    private int frameSize;
    private int nextDivisionLabel;
    private int nextStringLabel;
    private int nextGpParameter;
    private int nextXmmParameter;

    public String generate(IRProgram program) {
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
        if (entryFunction != null) {
            emitEntryPoint(asm, entryFunction);
        }

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
        return asm.toString();
    }

    private void appendHeader(StringBuilder asm) {
        asm.append("default rel\n");
        asm.append("extern printf\n");
        asm.append("section .data\n");
        asm.append("fmt_i32: db \"%d\", 10, 0\n");
        asm.append("fmt_i64: db \"%ld\", 10, 0\n\n");
        asm.append("fmt_f64: db \"%f\", 10, 0\n");
        asm.append("fmt_string: db \"%s\", 10, 0\n\n");
        if (!stringLabels.isEmpty()) {
            asm.append("section .rodata\n");
            for (Map.Entry<String, String> entry : stringLabels.entrySet()) {
                asm.append(entry.getValue()).append(": db ")
                        .append(utf8Bytes(entry.getKey())).append("\n");
            }
            asm.append('\n');
        }
        asm.append("section .text\n\n");
    }

    private void prepareStringLabels(List<IRInstruction> instructions) {
        stringLabels.clear();
        nextStringLabel = 0;
        for (IRInstruction instruction : instructions) {
            registerString(instruction.destination());
            if (instruction.opCode() == IROpCode.PRINT_FORMAT
                    && instruction.left() != null
                    && instruction.left().kind() == Operand.Kind.CONSTANT
                    && instruction.left().value() instanceof String format) {
                registerStringValue(normalizeBooleanFormat(format));
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

    private String normalizeBooleanFormat(String format) {
        StringBuilder normalized = new StringBuilder(format.length());
        for (int index = 0; index < format.length(); index++) {
            char current = format.charAt(index);
            if (current == '%' && index + 1 < format.length() && format.charAt(index + 1) == 'b') {
                normalized.append("%d");
                index++;
            } else {
                normalized.append(current);
            }
        }
        return normalized.toString();
    }

    private String utf8Bytes(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder output = new StringBuilder();
        for (byte valueByte : bytes) {
            if (!output.isEmpty()) output.append(", ");
            output.append(Byte.toUnsignedInt(valueByte));
        }
        if (!output.isEmpty()) output.append(", ");
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
            if (!isFunctionLabel(instruction)) continue;
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
        asm.append("global main\n");
        asm.append("main:\n");
        asm.append("    push rbp\n");
        asm.append("    mov rbp, rsp\n");
        asm.append("    mov rdi, rsi\n");
        asm.append("    call ").append(entryFunction).append("\n");
        asm.append("    xor eax, eax\n");
        asm.append("    leave\n");
        asm.append("    ret\n");
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
        if (instruction.left().type() == Operand.Type.F64) {
            loadF64(asm, instruction.left(), "xmm0");
            asm.append("    lea rdi, [rel fmt_f64]\n");
            asm.append("    mov eax, 1\n");
        } else if (instruction.left().type() == Operand.Type.REF) {
            loadI64(asm, instruction.left(), "rsi");
            asm.append("    lea rdi, [rel fmt_string]\n");
            asm.append("    xor eax, eax\n");
        } else if (instruction.left().type() == Operand.Type.I64) {
            loadI64(asm, instruction.left(), "rsi");
            asm.append("    lea rdi, [rel fmt_i64]\n");
            asm.append("    xor eax, eax\n");
        } else {
            loadI32(asm, instruction.left(), "esi");
            asm.append("    lea rdi, [rel fmt_i32]\n");
            asm.append("    xor eax, eax\n");
        }
        asm.append("    call printf\n");
    }

    private void emitFormattedPrint(StringBuilder asm, IRInstruction instruction) {
        String[] gpRegisters = {"rsi", "rdx", "rcx", "r8", "r9"};
        String[] xmmRegisters = {
                "xmm0", "xmm1", "xmm2", "xmm3",
                "xmm4", "xmm5", "xmm6", "xmm7"
        };
        List<Operand> arguments = instruction.arguments();
        int[] stackOffsetsByArgument = new int[arguments.size()];
        java.util.Arrays.fill(stackOffsetsByArgument, -1);

        int gpRegistersUsed = 0;
        int xmmRegistersUsed = 0;
        int stackArgumentCount = 0;
        for (int index = 0; index < arguments.size(); index++) {
            Operand argument = arguments.get(index);
            if (argument.type() == Operand.Type.F64) {
                if (xmmRegistersUsed < xmmRegisters.length) {
                    xmmRegistersUsed++;
                } else {
                    stackOffsetsByArgument[index] = stackArgumentCount++ * 8;
                }
            } else if (argument.type() == Operand.Type.I32 || argument.type() == Operand.Type.I64
                    || argument.type() == Operand.Type.BOOL || argument.type() == Operand.Type.REF) {
                if (gpRegistersUsed < gpRegisters.length) {
                    gpRegistersUsed++;
                } else {
                    stackOffsetsByArgument[index] = stackArgumentCount++ * 8;
                }
            } else {
                throw new UnsupportedOperationException(
                        "Tipo não suportado em printf: " + argument.type() + ".");
            }
        }

        int stackSize = align(stackArgumentCount * 8, 16);
        if (stackSize > 0) {
            asm.append("    sub rsp, ").append(stackSize).append("\n");
        }

        int gpIndex = 0;
        int xmmIndex = 0;
        for (int index = 0; index < arguments.size(); index++) {
            Operand argument = arguments.get(index);
            int stackOffset = stackOffsetsByArgument[index];
            if (argument.type() == Operand.Type.F64) {
                if (stackOffset >= 0) {
                    loadF64(asm, argument, "xmm15");
                    asm.append("    movsd qword [rsp+").append(stackOffset)
                            .append("], xmm15\n");
                } else {
                    loadF64(asm, argument, xmmRegisters[xmmIndex++]);
                }
            } else if (stackOffset >= 0) {
                if (argument.type() == Operand.Type.I32 || argument.type() == Operand.Type.BOOL) {
                    loadI32(asm, argument, "eax");
                    asm.append("    movsxd rax, eax\n");
                } else {
                    loadI64(asm, argument, "rax");
                }
                asm.append("    mov qword [rsp+").append(stackOffset).append("], rax\n");
            } else {
                String register = gpRegisters[gpIndex++];
                if (argument.type() == Operand.Type.I32 || argument.type() == Operand.Type.BOOL) {
                    register = register32(register);
                    loadI32(asm, argument, register);
                } else {
                    loadI64(asm, argument, register);
                }
            }
        }

        String format = normalizeBooleanFormat((String) instruction.left().value());
        asm.append("    lea rdi, [rel ").append(stringLabels.get(format)).append("]\n");
        asm.append("    mov eax, ").append(xmmIndex).append("\n");
        asm.append("    call printf\n");
        if (stackSize > 0) {
            asm.append("    add rsp, ").append(stackSize).append("\n");
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
            if (remainder) asm.append("    xor edx, edx\n");
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
            if (remainder) asm.append("    xor edx, edx\n");
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
        String[] gpRegisters = {"rdi", "rsi", "rdx", "rcx", "r8", "r9"};
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
            if (value == null) immediate = "0";
            else if (value instanceof Boolean booleanValue) immediate = booleanValue ? "1" : "0";
            else immediate = value.toString();
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
