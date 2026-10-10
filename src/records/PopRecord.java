package src.records;

import src.enums.TokenType;
import src.interfaces.StatementInterface;
import src.interfaces.visitor.ASTVisitor;

public record PopRecord(
        TokenType type,
        RegisterRecord register) implements StatementInterface {
    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
