package compiler.exceptions;

/** Indicates that an IR operation violates the backend's input contract. */
public final class InvalidIRException extends BackendException {
    public InvalidIRException(String message) {
        super(message);
    }
}
