package src.ast.records;

import src.lexer.TokenType;
import src.ast.ExpressionInterface;
import src.ast.StatementInterface;
import src.ast.visitor.ASTVisitor;

public record DeclarationRecord(
        TokenType type,
        String name,
        ExpressionInterface value) implements StatementInterface {

     @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
