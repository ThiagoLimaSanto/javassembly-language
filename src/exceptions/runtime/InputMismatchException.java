package src.exceptions.runtime;

public final class InputMismatchException extends NoSuchElementException {
    private static final long serialVersionUID = 1L;

    public InputMismatchException() {
        super("Entrada inválida para Scanner.");
    }
}
