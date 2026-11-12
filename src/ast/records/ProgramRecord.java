package src.ast.records;

import java.util.List;

import src.ast.ASTNode;
import src.ast.visitor.ASTVisitor;

public record ProgramRecord(
        List<ClassRecord> classes,
        List<InterfaceRecord> interfaces,
        List<RegisterDeclarationRecord> registers,
        String packageName,
        List<String> imports) implements ASTNode {

    public ProgramRecord(
            List<ClassRecord> classes,
            List<InterfaceRecord> interfaces,
            List<RegisterDeclarationRecord> registers) {
        this(classes, interfaces, registers, "", List.of());
    }

    public ProgramRecord(
            List<ClassRecord> classes,
            List<InterfaceRecord> interfaces) {
        this(classes, interfaces, List.of(), "", List.of());
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
