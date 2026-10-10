package src.records;

import src.interfaces.ExpressionInterface;
import src.interfaces.StatementInterface;
import src.interfaces.visitor.ASTVisitor;

public record ExpressionStatementRecord(
        ExpressionInterface expression) implements StatementInterface {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
