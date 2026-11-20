package compiler.exceptions.runtime;

public final class NumberFormatException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NumberFormatException() {
        super("Texto em formato inválido para conversão numérica.");
    }
}
