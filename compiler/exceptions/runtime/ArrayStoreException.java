package compiler.exceptions.runtime;

public final class ArrayStoreException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ArrayStoreException() {
        super("O valor não é compatível com o tipo real dos elementos do array.");
    }
}
