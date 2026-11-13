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

    public String generate(IRProgram program) {
        StringBuilder asm = new StringBuilder();
        asm.append("default rel\n");
        asm.append("extern printf\n");
        asm.append("section .data\n");
        asm.append("fmt_i32: db \"%d\", 10, 0\n\n");
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
        asm.append("section .text\n\n");
        emitFunction(asm, target);
        return asm.toString();
    }

    private boolean isFunctionLabel(IRInstruction instruction) {
        return instruction.opCode() == IROpCode.LABEL
                && instruction.destination().kind() == Operand.Kind.LABEL
                && instruction.destination().value().toString().contains(".");
    }

    private String findEntryFunction(IRProgram program) {
        for (IRInstruction instruction : program.instructions()) {
            if (isFunctionLabel(instruction)
                    && instruction.destination().value()
                            .toString().endsWith(".main")) {
                return instruction.destination().value().toString();
            }
        }
        return null;
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
            case F64, REF -> 8;
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
                loadI32(asm, instruction.left(), "eax");
                storeI32(asm, instruction.destination(), "eax");
            }

            case ADD -> {
                loadI32(asm, instruction.left(), "eax");
                loadI32(asm, instruction.right(), "ecx");
                asm.append("    add eax, ecx\n");
                storeI32(asm, instruction.destination(), "eax");
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
                    loadI32(asm, instruction.left(), "eax");
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
        loadI32(asm, instruction.left(), "esi");
        asm.append("    lea rdi, [rel fmt_i32]\n");
        asm.append("    xor eax, eax\n");
        asm.append("    call printf\n");
    }

    private void emitComparison(
            StringBuilder asm,
            IRInstruction instruction) {
        loadI32(asm, instruction.left(), "eax");
        loadI32(asm, instruction.right(), "ecx");
        asm.append("    cmp eax, ecx\n");

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
        String[] argumentRegisters = {
                "edi", "esi", "edx", "ecx", "r8d", "r9d"
        };

        if (instruction.arguments().size() > argumentRegisters.length) {
            throw new UnsupportedOperationException(
                    "Chamadas com mais de seis argumentos ainda não são suportadas.");
        }

        for (int i = 0; i < instruction.arguments().size(); i++) {
            loadI32(asm, instruction.arguments().get(i), argumentRegisters[i]);
        }

        asm.append("    call ")
                .append(instruction.left().value())
                .append("\n");

        if (instruction.destination() != null) {
            storeI32(asm, instruction.destination(), "eax");
        }
    }

    private void loadI32(
            StringBuilder asm,
            Operand source,
            String register) {
        if (source.kind() == Operand.Kind.CONSTANT) {
            asm.append("    mov ")
                    .append(register)
                    .append(", ")
                    .append(source.value())
                    .append("\n");
        } else {
            asm.append("    mov ")
                    .append(register)
                    .append(", dword ")
                    .append(address(source))
                    .append("\n");
        }
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
