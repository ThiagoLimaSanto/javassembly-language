package src.exceptions;

public class LexicalException extends LanguageException {
    public LexicalException(String message, int line, int column) {
        super("léxico", message, line, column);
    }
}
