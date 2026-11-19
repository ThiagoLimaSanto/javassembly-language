package src.exceptions.runtime;

public final class NegativeArraySizeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NegativeArraySizeException() {
        super("Tamanho negativo para o array.");
    }
}
