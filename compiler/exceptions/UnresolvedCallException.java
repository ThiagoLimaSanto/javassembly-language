package compiler.exceptions;

/** Signals that direct function lookup failed and instance lookup may be attempted. */
public final class UnresolvedCallException extends BackendException {
    public UnresolvedCallException(String message) {
        super(message);
    }
}
