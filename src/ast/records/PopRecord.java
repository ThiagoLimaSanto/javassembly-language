package src.ast.records;

import src.lexer.TokenType;
import src.ast.StatementInterface;
import src.ast.visitor.ASTVisitor;

public record PopRecord(
        TokenType type,
        RegisterRecord register) implements StatementInterface {
    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
