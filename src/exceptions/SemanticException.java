package src.exceptions;

public class SemanticException extends LanguageException {
    public SemanticException(String message, int line, int column) {
        super("semântico", message, line, column);
    }
}
