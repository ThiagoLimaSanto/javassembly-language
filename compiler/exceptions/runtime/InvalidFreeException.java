package compiler.exceptions.runtime;

public final class InvalidFreeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidFreeException() {
        super("free recebeu um ponteiro inválido ou já liberado.");
    }
}
