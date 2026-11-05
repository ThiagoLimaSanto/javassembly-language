package src.ast.records;

import java.util.List;

import src.ast.ASTNode;
import src.ast.visitor.ASTVisitor;

public record ProgramRecord(
        List<ClassRecord> classes,
        List<InterfaceRecord> interfaces) implements ASTNode {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
