package test;

import src.Frontend;
import src.ast.records.ProgramRecord;
import src.exceptions.SemanticException;
import src.semantic.EntryPointValidator;
import src.semantic.SemanticAnalyzer;

public final class EntryPointValidatorTests {
    private EntryPointValidatorTests() {
    }

    public static void main(String[] args) {
        expectEntryError("public class NoEntry { public static func void work() { return; } }",
                "Ponto de entrada não encontrado");
        expectEntryError("public class InvalidEntry { public func void main() { return; } }",
                "O método main deve ser public static void");
        expectEntryError("public class DuplicateEntry {"
                + " public static func void main() { return; }"
                + " public static func void main(String[] args) { return; } }",
                "exatamente um método main válido");
        expectAccepted("public class NoArgumentEntry {"
                + " public static func void main() { return; } }");
        expectAccepted("public class StringArrayEntry {"
                + " public static func void main(String[] args) { return; } }");
        System.out.println("Entry point validation checks passed.");
    }

    private static void expectAccepted(String source) {
        ProgramRecord program = Frontend.parse(source);
        new SemanticAnalyzer().visit(program);
        EntryPointValidator.validate(program);
    }

    private static void expectEntryError(String source, String messageFragment) {
        ProgramRecord program = Frontend.parse(source);
        new SemanticAnalyzer().visit(program);
        try {
            EntryPointValidator.validate(program);
            throw new AssertionError("Expected entry point error containing: " + messageFragment);
        } catch (SemanticException exception) {
            if (!exception.getMessage().contains(messageFragment)) {
                throw new AssertionError("Unexpected entry point error: " + exception.getMessage());
            }
        }
    }
}
