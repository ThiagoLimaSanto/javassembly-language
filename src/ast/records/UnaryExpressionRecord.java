package src.ast.records;

import src.ast.ExpressionInterface;
import src.ast.visitor.ASTVisitor;
import src.lexer.TokenType;

public record UnaryExpressionRecord(
        TokenType operator,
        ExpressionInterface operand,
        int line,
        int column) implements ExpressionInterface {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
