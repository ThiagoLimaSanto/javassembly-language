package compiler.ast.records;

import java.util.List;

import compiler.ast.ExpressionInterface;
import compiler.ast.visitor.ASTVisitor;

public record CallRecord(
        String name,
        List<ExpressionInterface> arguments,
        List<String> staticImports,
        int line,
        int column) implements ExpressionInterface {

    public CallRecord {
        arguments = List.copyOf(arguments);
        staticImports = List.copyOf(staticImports);
    }

    public CallRecord(
            String name,
            List<ExpressionInterface> arguments,
            int line,
            int column) {
        this(name, arguments, List.of(), line, column);
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }

}
