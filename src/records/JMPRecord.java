package src.records;

import src.interfaces.StatementInterface;
import src.interfaces.visitor.ASTVisitor;

public record JMPRecord(
        String name) implements StatementInterface {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return "JMP: " + name;
    }

}
