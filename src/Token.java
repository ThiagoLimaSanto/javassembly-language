package src;

import src.enums.TokenType;

public class Token {

    private TokenType type;
    private String lexema;
    private int line;
    private int column;

    public Token(TokenType type, String lexema, int line, int column) {
        this.type = type;
        this.lexema = lexema;
        this.line = line;
        this.column = column;
    }

    public TokenType getType() {
        return type;
    }

    public int getColumn() {
        return column;
    }

    public int getLine() {
        return line;
    }

    public String getLexema() {
        return lexema;
    }

    @Override
    public String toString() {
        return "<" + type + ", " + lexema + ", L" + line + ", C" + column + ">";
    }
}
