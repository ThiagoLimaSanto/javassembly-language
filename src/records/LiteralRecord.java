package src.records;

import src.interfaces.ExpressionInterface;

public record LiteralRecord(Object valor) implements ExpressionInterface {

    @Override
    public String toString() {
        return "Literal: " + AstFormat.literal(valor);
    }
}
