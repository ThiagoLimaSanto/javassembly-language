package src.ast.records;

import java.util.List;

import src.ast.ExpressionInterface;

public record ConstructorInvocationRecord(
        boolean delegatesToThis,
        List<ExpressionInterface> arguments,
        int line,
        int column) {
}
