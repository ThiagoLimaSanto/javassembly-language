package compiler.exceptions.runtime;

public class IncompatibleClassChangeError extends LinkageError {
    private static final long serialVersionUID = 1L;

    public IncompatibleClassChangeError(String message) {
        super(message);
    }
}
