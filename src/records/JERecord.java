package src.records;

import src.interfaces.StatementInterface;
import src.interfaces.visitor.ASTVisitor;

public record JERecord(
        String target) implements StatementInterface {
    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
