package src.ast.records;


import src.lexer.TokenType;
import src.ast.ExpressionInterface;
import src.ast.visitor.ASTVisitor;

public record BinaryExpressionRecord(
        TokenType operator,
        ExpressionInterface left,
        ExpressionInterface right) implements ExpressionInterface {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
