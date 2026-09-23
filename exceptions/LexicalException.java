package exceptions;

public class LexicalException extends RuntimeException {
    public LexicalException(String message, int line, int column) {
        super("Erro léxico na linha " + line
            + ", coluna " + column + ": " + message);
    }
}
