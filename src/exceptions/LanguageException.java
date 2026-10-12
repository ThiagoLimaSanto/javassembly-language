package src.exceptions;

public abstract class LanguageException extends RuntimeException {
    private final int line;
    private final int column;

    protected LanguageException(String stage, String message, int line, int column) {
        super("Erro " + stage + " na linha " + line + ", coluna " + column + ": " + message);

        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
