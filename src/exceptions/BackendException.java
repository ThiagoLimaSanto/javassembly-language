package src.exceptions;

/** Base class for errors found while lowering IR or emitting target code. */
public class BackendException extends LanguageException {
    public BackendException(String message) {
        super("backend", message, 0, 0);
    }

    public BackendException(String message, int line, int column) {
        super("backend", message, line, column);
    }

    public BackendException(String message, Throwable cause) {
        super("backend", message, 0, 0);
        initCause(cause);
    }
}
