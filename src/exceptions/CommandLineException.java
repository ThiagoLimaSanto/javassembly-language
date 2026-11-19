package src.exceptions;

public final class CommandLineException extends LanguageException {
    public CommandLineException(String message) {
        super("linha de comando", message, 0, 0);
    }
}
