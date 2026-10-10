package src.records;

import java.util.List;

import src.interfaces.StatementInterface;
import src.interfaces.visitor.ASTVisitor;

public record BlockRecord(List<StatementInterface> commands) implements StatementInterface {

     @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
