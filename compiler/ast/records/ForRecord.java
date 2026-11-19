package compiler.ast.records;

import compiler.ast.ExpressionInterface;
import compiler.ast.StatementInterface;
import compiler.ast.visitor.ASTVisitor;

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
