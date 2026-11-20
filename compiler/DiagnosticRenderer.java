package compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import compiler.exceptions.LanguageException;

/** Formats a compiler error with its source location and a small source excerpt. */
public final class DiagnosticRenderer {
    private DiagnosticRenderer() {
    }

    public static String render(Path sourceFile, LanguageException exception) {
        Path path = sourceFile.toAbsolutePath().normalize();
        int line = exception.getLine();
        int column = exception.getColumn();
        String heading = path + (line > 0 ? ":" + line + (column > 0 ? ":" + column : "") : "")
                + ": error: [" + exception.getClass().getSimpleName() + "] "
                + exception.getMessage();
        if (line < 1 || column < 1) return heading;

        try {
            List<String> lines = Files.readAllLines(path);
            if (line > lines.size()) return heading;
            String sourceLine = lines.get(line - 1);
            int width = Integer.toString(line).length();
            String expandedLine = expandTabs(sourceLine);
            int caretColumn = expandTabs(sourceLine.substring(0,
                    Math.min(sourceLine.length(), column - 1))).length();
            return heading + System.lineSeparator()
                    + " " + " ".repeat(width) + " | " + expandedLine + System.lineSeparator()
                    + " " + " ".repeat(width) + " | " + " ".repeat(caretColumn) + "^";
        } catch (IOException | RuntimeException ignored) {
            return heading;
        }
    }

    private static String expandTabs(String value) {
        return value.replace("\t", "    ");
    }
}
