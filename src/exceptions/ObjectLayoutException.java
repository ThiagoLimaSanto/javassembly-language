package src.exceptions;

/** Reports a type or inheritance shape that cannot be laid out by the backend. */
public final class ObjectLayoutException extends BackendException {
    public ObjectLayoutException(String message) {
        super(message);
    }
}
