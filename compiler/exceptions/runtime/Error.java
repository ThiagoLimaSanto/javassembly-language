package compiler.exceptions.runtime;

public class Error extends Throwable {
    private static final long serialVersionUID = 1L;

    public Error(String message) {
        super(message);
    }
}
