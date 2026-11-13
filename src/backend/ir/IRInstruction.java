package src.backend.ir;

import java.util.List;

public record IRInstruction(
        IROpCode opCode,
        Operand destination,
        Operand left,
        Operand right,
        List<Operand> arguments) {

    public IRInstruction {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    public IRInstruction(
            IROpCode opCode,
            Operand destination,
            Operand left,
            Operand right) {
        this(opCode, destination, left, right, List.of());
    }
}