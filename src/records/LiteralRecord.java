package src.records;

import src.interfaces.ExpressionInterface;
import src.interfaces.visitor.ASTVisitor;

public record LiteralRecord(Object valor) implements ExpressionInterface {

    @Override
    public String toString() {
        return "Literal: " + AstFormat.literal(valor);
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
