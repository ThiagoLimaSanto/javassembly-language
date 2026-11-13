package src.backend.asm;

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
    private int frameSize;
    private int nextDivisionLabel;

    public String generate(IRProgram program) {
        StringBuilder asm = new StringBuilder();
        asm.append("default rel\n");
        asm.append("extern printf\n");
        asm.append("section .data\n");
        asm.append("fmt_i32: db \"%d\", 10, 0\n\n");
        asm.append("fmt_i64: db \"%ld\", 10, 0\n\n");
        asm.append("section .text\n\n");

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
        asm.append("default rel\n");
        asm.append("extern printf\n");
        asm.append("section .data\n");
        asm.append("fmt_i32: db \"%d\", 10, 0\n");
        asm.append("fmt_i64: db \"%ld\", 10, 0\n\n");
        asm.append("section .text\n\n");
        emitFunction(asm, target);
        return asm.toString();
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
            case I64, REF -> 8;
            case F64 -> throw new UnsupportedOperationException("Operando F64 não suportado pelo NASM.");
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
                int index = (Integer) instruction.left().value();
                Operand destination = instruction.destination();

                String[] intRegisters = {
                        "edi", "esi", "edx", "ecx", "r8d", "r9d"
                };
                String[] refRegisters = {
                        "rdi", "rsi", "rdx", "rcx", "r8", "r9"
                };

                if (index >= intRegisters.length) {
                    throw new UnsupportedOperationException(
                            "Parâmetro além do sexto ainda não suportado.");
                }

                if (destination.type() == Operand.Type.REF) {
                    asm.append("    mov qword ")
                            .append(address(destination))
                            .append(", ")
                            .append(refRegisters[index])
                            .append("\n");
                } else if (destination.type() == Operand.Type.I64) {
                    asm.append("    mov qword ")
                            .append(address(destination))
                            .append(", ")
                            .append(refRegisters[index])
                            .append("\n");
                } else if (destination.type() == Operand.Type.I32
                        || destination.type() == Operand.Type.BOOL) {
                    asm.append("    mov dword ")
                            .append(address(destination))
                            .append(", ")
                            .append(intRegisters[index])
                            .append("\n");
                } else {
                    throw new UnsupportedOperationException(
                            "Tipo de parâmetro ainda não suportado: "
                                    + destination.type());
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

            case ADD -> {
                emitIntegerBinary(asm, instruction, "add");
            }

            case SUB, MUL -> emitIntegerBinary(asm, instruction,
                    instruction.opCode() == IROpCode.SUB ? "sub" : "imul");

            case DIV -> emitDivision(asm, instruction, false);

            case MOD -> emitDivision(asm, instruction, true);

            case NEG -> {
                if (instruction.destination().type() == Operand.Type.I64) {
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

            case RETURN -> {
                if (instruction.left() != null) {
                    if (instruction.left().type() == Operand.Type.I64
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
        if (instruction.left().type() == Operand.Type.I64) {
            loadI64(asm, instruction.left(), "rsi");
            asm.append("    lea rdi, [rel fmt_i64]\n");
        } else {
            loadI32(asm, instruction.left(), "esi");
            asm.append("    lea rdi, [rel fmt_i32]\n");
        }
        asm.append("    xor eax, eax\n");
        asm.append("    call printf\n");
    }

    private void emitIntegerBinary(
            StringBuilder asm,
            IRInstruction instruction,
            String opcode) {
        if (instruction.destination().type() == Operand.Type.I64) {
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
        String[] intArgumentRegisters = {
                "edi", "esi", "edx", "ecx", "r8d", "r9d"
        };
        String[] wideArgumentRegisters = {"rdi", "rsi", "rdx", "rcx", "r8", "r9"};

        if (instruction.arguments().size() > intArgumentRegisters.length) {
            throw new UnsupportedOperationException(
                    "Chamadas com mais de seis argumentos ainda não são suportadas.");
        }

        for (int i = 0; i < instruction.arguments().size(); i++) {
            Operand argument = instruction.arguments().get(i);
            if (argument.type() == Operand.Type.I64 || argument.type() == Operand.Type.REF) {
                loadI64(asm, argument, wideArgumentRegisters[i]);
            } else {
                loadI32(asm, argument, intArgumentRegisters[i]);
            }
        }

        asm.append("    call ")
                .append(instruction.left().value())
                .append("\n");

        if (instruction.destination() != null) {
            if (instruction.destination().type() == Operand.Type.I64
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
        } else if (destination.type() == Operand.Type.I64 || destination.type() == Operand.Type.REF) {
            loadI64(asm, source, "rax");
            storeI64(asm, destination, "rax");
        } else {
            throw new UnsupportedOperationException("Atribuição F64 ainda não é suportada.");
        }
    }

    private void loadI64(StringBuilder asm, Operand source, String register) {
        if (source.kind() == Operand.Kind.CONSTANT) {
            Object value = source.value();
            String immediate;
            if (value == null) immediate = "0";
            else if (value instanceof Boolean booleanValue) immediate = booleanValue ? "1" : "0";
            else immediate = value.toString();
            asm.append("    mov ").append(register).append(", ").append(immediate).append("\n");
            return;
        }
        if (source.type() == Operand.Type.I32 || source.type() == Operand.Type.BOOL) {
            String lowRegister = register32(register);
            asm.append("    movsxd ").append(register).append(", dword ")
                    .append(address(source)).append("\n");
            if (lowRegister == null) {
                throw new IllegalArgumentException("Registrador de 64 bits inválido: " + register);
            }
            return;
        }
        asm.append("    mov ").append(register).append(", qword ")
                .append(address(source)).append("\n");
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
