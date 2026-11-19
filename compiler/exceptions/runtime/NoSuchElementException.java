package compiler.exceptions.runtime;

public class NoSuchElementException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NoSuchElementException() {
        this("Não há mais elementos disponíveis na entrada.");
    }

    public NoSuchElementException(String message) {
        super(message);
    }
}
