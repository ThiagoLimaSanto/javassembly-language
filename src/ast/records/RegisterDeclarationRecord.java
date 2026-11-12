package src.ast.records;

import src.ast.ASTNode;
import src.ast.ExpressionInterface;
import src.ast.visitor.ASTVisitor;

public record RegisterDeclarationRecord(
        RegisterRecord register,
        ExpressionInterface value) implements ASTNode {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
