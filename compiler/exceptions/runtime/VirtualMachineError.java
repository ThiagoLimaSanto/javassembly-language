package compiler.exceptions.runtime;

public class VirtualMachineError extends Error {
    private static final long serialVersionUID = 1L;

    public VirtualMachineError(String message) {
        super(message);
    }
}
