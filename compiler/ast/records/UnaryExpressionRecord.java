package compiler.ast.records;

import compiler.ast.ExpressionInterface;
import compiler.ast.visitor.ASTVisitor;
import compiler.lexer.TokenType;

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
