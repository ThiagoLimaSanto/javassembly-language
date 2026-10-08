package src.records;

import src.interfaces.ExpressionInterface;
import src.interfaces.StatementInterface;

public record WhileRecord(ExpressionInterface expression, BlockRecord block) implements StatementInterface {

    @Override
    public String toString() {
        return AstFormat.node("While", AstFormat.node("Condição", expression), block);
    }
}
