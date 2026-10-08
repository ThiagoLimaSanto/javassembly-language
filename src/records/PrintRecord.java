package src.records;

import src.interfaces.ExpressionInterface;
import src.interfaces.StatementInterface;
import src.interfaces.visitor.ASTVisitor;

public record PrintRecord(ExpressionInterface expression) implements StatementInterface {

    @Override
    public String toString() {
        return AstFormat.node("Print", expression);
    }

     @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
