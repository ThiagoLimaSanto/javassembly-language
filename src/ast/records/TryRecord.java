package src.ast.records;

import java.util.List;
import src.ast.StatementInterface;
import src.ast.visitor.ASTVisitor;

public record TryRecord(BlockRecord tryBlock, List<CatchRecord> catches,
        BlockRecord finallyBlock, int line, int column) implements StatementInterface {
    public TryRecord { catches = List.copyOf(catches); }
    @Override public void accept(ASTVisitor visitor) { visitor.visit(this); }
}
