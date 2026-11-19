package compiler.ast.records;

import java.util.List;

import compiler.ast.ExpressionInterface;

public record ConstructorInvocationRecord(
        boolean delegatesToThis,
        List<ExpressionInterface> arguments,
        int line,
        int column) {

    public ConstructorInvocationRecord {
        arguments = List.copyOf(arguments);
    }
}
