package src.records;

import src.interfaces.ExpressionInterface;
import src.interfaces.visitor.ASTVisitor;

public record LiteralRecord(Object valor) implements ExpressionInterface {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
