package src.exceptions;

public abstract class LanguageException extends RuntimeException {
    private final String stage;
    private final int line;
    private final int column;

    protected LanguageException(String stage, String message, int line, int column) {
        super(formatMessage(stage, message, line, column));

        this.stage = stage;
        this.line = line;
        this.column = column;
    }

    private static String formatMessage(String stage, String message, int line, int column) {
        String normalizedStage = requireText(stage, "stage");
        String normalizedMessage = requireText(message, "message");
        String location = line > 0
                ? " na linha " + line + (column > 0 ? ", coluna " + column : "")
                : "";
        return "Erro " + normalizedStage + location + ": " + normalizedMessage;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("O campo '" + name + "' da exceção não pode ser vazio.");
        }
        return value;
    }

    public String getStage() {
        return stage;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
