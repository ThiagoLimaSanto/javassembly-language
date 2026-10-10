package src.lexer;

import src.lexer.TokenType;

public class Token {

    private TokenType type;
    private String lexeme;
    private int line;
    private int column;

    public Token(TokenType type, String lexeme, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
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

    public String getLexeme() {
        return lexeme;
    }

    @Override
    public String toString() {
        return "<" + type + ", " + lexeme + ", L" + line + ", C" + column + ">";
    }
}
