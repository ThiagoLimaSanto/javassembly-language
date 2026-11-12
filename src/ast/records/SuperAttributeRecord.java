package src.ast.records;

import src.ast.ExpressionInterface;
import src.ast.visitor.ASTVisitor;

public record SuperAttributeRecord(
    String name,
    int line,
    int column
) implements ExpressionInterface {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}