package compiler.ast.records;

import compiler.ast.ASTNode;
import compiler.ast.ExpressionInterface;
import compiler.ast.visitor.ASTVisitor;

public record RegisterDeclarationRecord(
        RegisterRecord register,
        ExpressionInterface value) implements ASTNode {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
