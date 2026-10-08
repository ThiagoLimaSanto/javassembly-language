package src.records;

import src.interfaces.ExpressionInterface;
import src.interfaces.StatementInterface;
import src.interfaces.visitor.ASTVisitor;

public record ReturnRecord(
        ExpressionInterface value) implements StatementInterface {
    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return value == null
                ? "Return"
                : AstFormat.node("Return", value);
    }
}
