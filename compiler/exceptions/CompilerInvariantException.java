package compiler.exceptions;

/** Indicates an impossible compiler state, usually caused by an implementation bug. */
public final class CompilerInvariantException extends LanguageException {
    public CompilerInvariantException(String message) {
        super("interno do compilador", message, 0, 0);
    }

    public CompilerInvariantException(String message, Throwable cause) {
        super("interno do compilador", message, 0, 0);
        initCause(cause);
    }
}
