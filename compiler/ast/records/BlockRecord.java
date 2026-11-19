package compiler.ast.records;

import java.util.List;

import compiler.ast.StatementInterface;
import compiler.ast.visitor.ASTVisitor;

public record BlockRecord(List<StatementInterface> commands) implements StatementInterface {

    public BlockRecord {
        commands = List.copyOf(commands);
    }

     @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
