package src.exceptions.runtime;

public class NoSuchElementException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NoSuchElementException(String message) {
        super(message);
    }
}
