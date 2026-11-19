package compiler.ast.records;

import java.util.List;

import compiler.ast.ExpressionInterface;
import compiler.ast.visitor.ASTVisitor;

public record ArrayInitializerRecord(
        List<ExpressionInterface> elements,
        int line,
        int column) implements ExpressionInterface {

    public ArrayInitializerRecord {
        elements = List.copyOf(elements);
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
