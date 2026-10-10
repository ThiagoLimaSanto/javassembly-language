package src.ast.records;

import src.ast.StatementInterface;
import src.ast.visitor.ASTVisitor;

public record JERecord(
        String target) implements StatementInterface {
    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
