package src.exceptions.runtime;

public final class OutOfMemoryError extends VirtualMachineError {
    private static final long serialVersionUID = 1L;

    public OutOfMemoryError() {
        super("Não foi possível alocar memória para o objeto ou array.");
    }
}
