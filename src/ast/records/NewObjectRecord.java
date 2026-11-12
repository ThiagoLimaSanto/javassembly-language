package src.ast.records;

import java.util.List;

import src.ast.ExpressionInterface;
import src.ast.visitor.ASTVisitor;

public record NewObjectRecord(
        TypeRecord type,
        List<ExpressionInterface> arguments,
        int line,
        int column) implements ExpressionInterface {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
