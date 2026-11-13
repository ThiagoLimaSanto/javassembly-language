package src;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import src.ast.records.ProgramRecord;
import src.ast.records.ClassRecord;
import src.ast.records.InterfaceRecord;
import src.exceptions.SemanticException;
import src.lexer.Lexer;
import src.lexer.Token;
import src.parser.Parser;

public final class Frontend {

    private Frontend() {
    }

    public static ProgramRecord parse(String source) {
        return parse(source, Set.of());
    }

    static ProgramRecord parse(String source, Set<String> knownTypes) {
        List<Token> tokens = new Lexer(source).analyze();
        ProgramRecord program = new Parser(tokens, knownTypes).analyze();
        validateImportedTypeNames(program);
        return program;
    }

    public static ProgramRecord loadProject(Path sourceFile) throws IOException {
        return new SourceProjectLoader().load(sourceFile);
    }

    private static void validateImportedTypeNames(ProgramRecord program) {
        if (program.imports().isEmpty()) {
            return;
        }

        for (ClassRecord classNode : program.classes()) {
            validateImportedTypeName(
                    simpleName(classNode.name()),
                    classNode.line(),
                    classNode.column(),
                    program.imports());
        }
        for (InterfaceRecord interfaceNode : program.interfaces()) {
            validateImportedTypeName(
                    simpleName(interfaceNode.name()),
                    interfaceNode.line(),
                    interfaceNode.column(),
                    program.imports());
        }
    }

    private static void validateImportedTypeName(
            String declaredName,
            int line,
            int column,
            List<String> imports) {
        for (String importedName : imports) {
            if (importedName.startsWith("static:") || importedName.endsWith(".*")) {
                continue;
            }
            if (declaredName.equals(simpleName(importedName))) {
                throw new SemanticException(
                        "O tipo declarado '" + declaredName
                                + "' conflita com o tipo importado '" + importedName + "'.",
                        line,
                        column);
            }
        }
    }

    private static String simpleName(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }
}
