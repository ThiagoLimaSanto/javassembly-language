package compiler.semantic;

import java.util.ArrayList;
import java.util.List;

import compiler.ast.records.ClassRecord;
import compiler.ast.records.FunctionRecord;
import compiler.ast.records.ProgramRecord;
import compiler.exceptions.SemanticException;
import compiler.lexer.TokenType;

/** Validates the method invoked by the native process entry point. */
public final class EntryPointValidator {
    private EntryPointValidator() {
    }

    public static FunctionRecord validate(ProgramRecord program) {
        List<FunctionRecord> validEntries = new ArrayList<>();
        List<FunctionRecord> namedMain = new ArrayList<>();
        for (ClassRecord classNode : program.classes()) {
            for (FunctionRecord function : classNode.functions()) {
                if (!function.name().equals("main")) continue;
                namedMain.add(function);
                if (isValidEntry(function)) validEntries.add(function);
            }
        }

        if (validEntries.size() > 1) {
            FunctionRecord duplicate = validEntries.get(1);
            throw new SemanticException(
                    "O programa deve declarar exatamente um método main válido; foram encontrados "
                            + validEntries.size() + ".",
                    duplicate.line(), duplicate.column());
        }
        if (validEntries.isEmpty()) {
            if (namedMain.isEmpty()) {
                throw new SemanticException(
                        "Ponto de entrada não encontrado. Declare `public static func void main(String[] args)` "
                                + "ou `public static func void main()`.",
                        1, 1);
            }
            FunctionRecord invalid = namedMain.get(0);
            throw new SemanticException(
                    "O método main deve ser public static void e aceitar String[] ou nenhum argumento.",
                    invalid.line(), invalid.column());
        }
        return validEntries.get(0);
    }

    private static boolean isValidEntry(FunctionRecord function) {
        if (!function.isStatic()
                || function.visibility() != TokenType.PUBLIC
                || function.isAbstract()
                || function.isNative()
                || function.block() == null
                || !function.returnType().name().equals("void")) {
            return false;
        }
        if (function.parameters().isEmpty()) return true;
        return function.parameters().size() == 1
                && function.parameters().get(0).type().name().equals("String[]");
    }
}
