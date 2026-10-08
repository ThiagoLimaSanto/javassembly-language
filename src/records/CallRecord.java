package src.records;

import java.util.List;

import src.interfaces.ExpressionInterface;
import src.interfaces.visitor.ASTVisitor;

public record CallRecord(
        String name,
        List<ExpressionInterface> arguments) implements ExpressionInterface {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }

}
