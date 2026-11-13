package src.ast.records;

import src.ast.ExpressionInterface;
import src.ast.visitor.ASTVisitor;
import src.lexer.TokenType;

public record LiteralRecord(Object value) implements ExpressionInterface {

    public TokenType getType() {
        if (value instanceof Integer) {
            return TokenType.INT;
        }

        if (value instanceof Long) {
            return TokenType.LONG;
        }

        if (value instanceof Double || value instanceof Float) {
            return TokenType.DOUBLE;
        }

        if (value instanceof String) {
            return TokenType.STRING;
        }

        if (value instanceof Boolean) {
            return TokenType.BOOLEAN;
        }

        if (value == null) {
            return TokenType.NULL;
        }

        return null;
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
