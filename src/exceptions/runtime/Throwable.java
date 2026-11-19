package src.exceptions.runtime;

public class Throwable {
    private final String message;

    public Throwable(String message) {
        this.message = message;
    }

    public final String getMessage() {
        return message;
    }

    public final String diagnostic() {
        return getClass().getSimpleName() + ": " + getMessage() + "\n";
    }
}
