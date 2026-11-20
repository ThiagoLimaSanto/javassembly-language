package test;

import java.nio.file.Files;
import java.nio.file.Path;

import compiler.DiagnosticRenderer;
import compiler.exceptions.SemanticException;

public final class DiagnosticRendererTests {
    private DiagnosticRendererTests() {
    }

    public static void main(String[] args) throws Exception {
        Path source = Files.createTempFile("jvs-diagnostic-", ".jvs");
        try {
            Files.writeString(source, "public class Example {\n    int value = ;\n}\n");
            String diagnostic = DiagnosticRenderer.render(source,
                    new SemanticException("nome inválido", 2, 17));
            if (!diagnostic.contains(source.toAbsolutePath().normalize() + ":2:17: error:")) {
                throw new AssertionError("Diagnostic should contain the path and exact location: " + diagnostic);
            }
            if (!diagnostic.contains("    int value = ;")) {
                throw new AssertionError("Diagnostic should print the source line: " + diagnostic);
            }
            String[] lines = diagnostic.split("\\R");
            if (lines.length != 3 || !lines[2].endsWith("^")) {
                throw new AssertionError("Diagnostic should point to the error column: " + diagnostic);
            }
        } finally {
            Files.deleteIfExists(source);
        }
        System.out.println("Diagnostic rendering checks passed.");
    }
}
