package test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import compiler.Frontend;
import compiler.ast.records.ProgramRecord;
import compiler.exceptions.SemanticException;
import compiler.semantic.EntryPointValidator;
import compiler.semantic.SemanticAnalyzer;

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
        ProgramRecord program = loadProject(source);
        new SemanticAnalyzer().visit(program);
        EntryPointValidator.validate(program);
    }

    private static void expectEntryError(String source, String messageFragment) {
        ProgramRecord program = loadProject(source);
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

    private static ProgramRecord loadProject(String source) {
        Path temporaryDirectory = null;
        try {
            temporaryDirectory = Files.createTempDirectory("javassembly-entry-point-");
            Path sourceFile = temporaryDirectory.resolve("EntryPointTest.jvs");
            Files.writeString(sourceFile, source);
            return Frontend.loadProject(sourceFile);
        } catch (IOException exception) {
            throw new AssertionError("Could not prepare entry-point test source.", exception);
        } finally {
            if (temporaryDirectory != null) {
                try (var paths = Files.walk(temporaryDirectory)) {
                    paths.sorted(java.util.Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException exception) {
                                    throw new java.io.UncheckedIOException(exception);
                                }
                            });
                } catch (IOException exception) {
                    throw new AssertionError("Could not clean up entry-point test source.", exception);
                }
            }
        }
    }
}
