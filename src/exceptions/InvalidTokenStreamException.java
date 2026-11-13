package src.exceptions;

public class InvalidTokenStreamException extends IllegalArgumentException {
    public InvalidTokenStreamException(String message) {
        super(message);
    }
}
