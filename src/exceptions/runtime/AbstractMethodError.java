package src.exceptions.runtime;

public final class AbstractMethodError extends IncompatibleClassChangeError {
    private static final long serialVersionUID = 1L;

    public AbstractMethodError() {
        super("O objeto não possui implementação para esse método.");
    }
}
