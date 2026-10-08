package src.records;

import src.interfaces.ExpressionInterface;
import src.interfaces.StatementInterface;

public record PrintRecord(ExpressionInterface expression) implements StatementInterface {

    @Override
    public String toString() {
        return AstFormat.node("Print", expression);
    }
}
