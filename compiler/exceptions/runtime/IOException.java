package compiler.exceptions.runtime;

public final class IOException extends Exception {
    private static final long serialVersionUID = 1L;

    public IOException() {
        super("Falha ao ler ou escrever no fluxo padrão.");
    }
}
