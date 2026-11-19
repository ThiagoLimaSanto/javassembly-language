package compiler.ast.records;

import compiler.ast.ExpressionInterface;
import compiler.ast.visitor.ASTVisitor;

public record MemberAccessRecord(
        ExpressionInterface receiver,
        String name,
        int line,
        int column) implements ExpressionInterface {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
