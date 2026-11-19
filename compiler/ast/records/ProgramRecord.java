package compiler.ast.records;

import java.util.List;
import java.util.Map;

import compiler.ast.ASTNode;
import compiler.ast.visitor.ASTVisitor;

public record ProgramRecord(
        List<ClassRecord> classes,
        List<InterfaceRecord> interfaces,
        List<RegisterDeclarationRecord> registers,
        String packageName,
        List<String> imports,
        Map<String, String> sourceFiles) implements ASTNode {

    public ProgramRecord {
        classes = List.copyOf(classes);
        interfaces = List.copyOf(interfaces);
        registers = List.copyOf(registers);
        imports = List.copyOf(imports);
        sourceFiles = Map.copyOf(sourceFiles);
    }

    public ProgramRecord(List<ClassRecord> classes, List<InterfaceRecord> interfaces,
            List<RegisterDeclarationRecord> registers, String packageName, List<String> imports) {
        this(classes, interfaces, registers, packageName, imports, Map.of());
    }

    public ProgramRecord(
            List<ClassRecord> classes,
            List<InterfaceRecord> interfaces,
            List<RegisterDeclarationRecord> registers) {
        this(classes, interfaces, registers, "", List.of(), Map.of());
    }

    public ProgramRecord(
            List<ClassRecord> classes,
            List<InterfaceRecord> interfaces) {
        this(classes, interfaces, List.of(), "", List.of(), Map.of());
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
