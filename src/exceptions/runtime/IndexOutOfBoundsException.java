package src.exceptions.runtime;

public class IndexOutOfBoundsException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public IndexOutOfBoundsException() {
        super("Índice fora dos limites do array.");
    }
}
