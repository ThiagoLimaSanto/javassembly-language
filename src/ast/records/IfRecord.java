package src.ast.records;

import src.ast.ExpressionInterface;
import src.ast.StatementInterface;
import src.ast.visitor.ASTVisitor;

public record IfRecord(
        ExpressionInterface condition,
        StatementInterface thenBranch,
        StatementInterface elseBranch,
        int line,
        int column) implements StatementInterface {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
