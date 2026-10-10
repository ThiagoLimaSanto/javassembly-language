package src.ast.records;

import java.util.List;

import src.ast.ExpressionInterface;
import src.ast.visitor.ASTVisitor;

public record CallRecord(
        String name,
        List<ExpressionInterface> arguments) implements ExpressionInterface {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }

}
