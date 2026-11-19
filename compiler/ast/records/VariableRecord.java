package compiler.ast.records;

import java.util.List;

import compiler.ast.ExpressionInterface;
import compiler.ast.visitor.ASTVisitor;

public record VariableRecord(
        String name,
        List<String> staticImports,
        int line,
        int column) implements ExpressionInterface {

    public VariableRecord {
        staticImports = List.copyOf(staticImports);
    }

    public VariableRecord(String name, int line, int column) {
        this(name, List.of(), line, column);
    }

     @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
