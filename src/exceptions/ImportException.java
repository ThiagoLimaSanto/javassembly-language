package src.exceptions;

public class ImportException extends SemanticException {
    public ImportException(String message, int line, int column) {
        super(message, line, column);
    }
}
