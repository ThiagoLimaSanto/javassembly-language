package compiler.ast.records;

import java.util.List;

import compiler.ast.ExpressionInterface;
import compiler.ast.visitor.ASTVisitor;

public record ThisCallRecord(
        String name,
        List<ExpressionInterface> arguments,
        int line,
        int column) implements ExpressionInterface {

    public ThisCallRecord {
        arguments = List.copyOf(arguments);
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
