package src.ast.records;

import java.util.List;

import src.ast.ExpressionInterface;
import src.ast.visitor.ASTVisitor;

public record CallRecord(
        String name,
        List<ExpressionInterface> arguments,
        int line,
        int column) implements ExpressionInterface {

    public CallRecord {
        arguments = List.copyOf(arguments);
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }

}
