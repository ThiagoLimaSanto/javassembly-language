package src.ast.records;

import src.ast.ExpressionInterface;
import src.ast.StatementInterface;
import src.ast.visitor.ASTVisitor;

public record ForRecord(
        StatementInterface initializer,
        ExpressionInterface condition,
        StatementInterface update,
        BlockRecord block,
        int line,
        int column) implements StatementInterface {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
