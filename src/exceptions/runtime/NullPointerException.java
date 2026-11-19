package src.exceptions.runtime;

public final class NullPointerException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NullPointerException() {
        super("Tentativa de acessar uma referência null.");
    }
}
