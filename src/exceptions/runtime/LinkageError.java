package src.exceptions.runtime;

public class LinkageError extends Error {
    private static final long serialVersionUID = 1L;

    public LinkageError(String message) {
        super(message);
    }
}
