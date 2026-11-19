package compiler.ast.records;

import java.util.List;

import compiler.ast.ExpressionInterface;
import compiler.ast.visitor.ASTVisitor;

public record ArrayCreationRecord(
        TypeRecord type,
        List<ExpressionInterface> dimensions,
        ExpressionInterface initializer,
        int line,
        int column) implements ExpressionInterface {

    public ArrayCreationRecord {
        dimensions = List.copyOf(dimensions);
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
