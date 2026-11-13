package src.exceptions;

public class SemanticException extends LanguageException {
    public SemanticException(String message, int line, int column) {
        this("semântico", message, line, column);
    }

    protected SemanticException(String stage, String message, int line, int column) {
        super(stage, message, line, column);
    }
}
