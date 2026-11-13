package src.lexer;

public class Token {

    private final TokenType type;
    private final String lexeme;
    private final int line;
    private final int column;

    public Token(TokenType type, String lexeme, int line, int column) {
        this.type = java.util.Objects.requireNonNull(type, "type");
        this.lexeme = java.util.Objects.requireNonNull(lexeme, "lexeme");
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
