package compiler.ast.records;

import compiler.ast.ASTNode;
import compiler.ast.visitor.ASTVisitor;

public record ParameterRecord(
        TypeRecord type,
        String name, int line, int column) implements ASTNode {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}