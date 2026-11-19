package src.exceptions.runtime;

public final class ArithmeticException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ArithmeticException() {
        super("Divisão ou resto por zero.");
    }
}
