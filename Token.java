public class Token {

    private TokenType tipo;
    private String lexema;
    private int linha;
    private int column;

    public Token(TokenType tipo, String lexema, int linha, int column) {
        this.tipo = tipo;
        this.lexema = lexema;
        this.linha = linha;
        this.column = column;
    }

    public TokenType getTipo() {
        return tipo;
    }

    public int getColumna() {
        return column;
    }

    public int getLinha() {
        return linha;
    }

    public String getLexema() {
        return lexema;
    }

    @Override
    public String toString() {
        return "<" + tipo + ", " + lexema + ", " + linha + ", " + column + ">";
    }
}
