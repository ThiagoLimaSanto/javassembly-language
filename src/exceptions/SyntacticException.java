package src.exceptions;

public class SyntacticException extends LanguageException {

    public SyntacticException(String message, int line, int column) {
        super("sintático", message, line, column);
    }
}
