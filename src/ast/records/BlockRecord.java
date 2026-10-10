package src.ast.records;

import java.util.List;

import src.ast.StatementInterface;
import src.ast.visitor.ASTVisitor;

public record BlockRecord(List<StatementInterface> commands) implements StatementInterface {

     @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
