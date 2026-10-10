package src.ast.records;

import src.ast.ExpressionInterface;
import src.ast.visitor.ASTVisitor;

public record LiteralRecord(Object value) implements ExpressionInterface {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
