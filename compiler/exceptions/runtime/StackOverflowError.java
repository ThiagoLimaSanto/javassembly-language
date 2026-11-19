package compiler.exceptions.runtime;

public final class StackOverflowError extends VirtualMachineError {
    private static final long serialVersionUID = 1L;

    public StackOverflowError() {
        super("A pilha interna de tratadores de exceção foi excedida.");
    }
}
