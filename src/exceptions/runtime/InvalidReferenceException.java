package src.exceptions.runtime;

public final class InvalidReferenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidReferenceException() {
        super("A referência aponta para memória inválida ou já liberada.");
    }
}
