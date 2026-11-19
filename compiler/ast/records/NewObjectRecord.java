package compiler.ast.records;

import java.util.List;

import compiler.ast.ExpressionInterface;
import compiler.ast.visitor.ASTVisitor;

public record NewObjectRecord(
        TypeRecord type,
        List<ExpressionInterface> arguments,
        int line,
        int column) implements ExpressionInterface {

    public NewObjectRecord {
        arguments = List.copyOf(arguments);
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
