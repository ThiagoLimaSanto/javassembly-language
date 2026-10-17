package src.ast.records;

import src.ast.ASTNode;
import src.ast.ExpressionInterface;
import src.ast.visitor.ASTVisitor;
import src.lexer.TokenType;

public record AttributeRecord(
        TokenType visibility,
        boolean isStatic,
        boolean isFinal,
        TypeRecord type,
        String name,
        ExpressionInterface value) implements ASTNode {
    @Override

    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
