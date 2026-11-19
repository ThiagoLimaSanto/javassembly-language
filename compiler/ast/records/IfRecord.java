package compiler.ast.records;

import compiler.ast.ExpressionInterface;
import compiler.ast.StatementInterface;
import compiler.ast.visitor.ASTVisitor;

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
