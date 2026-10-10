package src.lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import src.lexer.states.InitialState;
import src.lexer.states.LexerState;
import src.lexer.states.StringState;

public class Lexer {

    private String source;
    private int currentPosition;
    private int line = 1;
    private int column = 1;
    private List<Token> tokens = new ArrayList<>();
    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("class", TokenType.CLASS),
            Map.entry("func", TokenType.FUNC),
            Map.entry("main", TokenType.MAIN),
            Map.entry("public", TokenType.PUBLIC),
            Map.entry("private", TokenType.PRIVATE),
            Map.entry("int", TokenType.INT),
            Map.entry("double", TokenType.DOUBLE),
            Map.entry("boolean", TokenType.BOOLEAN),
            Map.entry("String", TokenType.STRING),
            Map.entry("void", TokenType.VOID),

            Map.entry("add", TokenType.ADD),
            Map.entry("sub", TokenType.SUB),
            Map.entry("mul", TokenType.MUL),
            Map.entry("div", TokenType.DIV),
            Map.entry("mov", TokenType.MOV),
            Map.entry("cmp", TokenType.CMP),
            Map.entry("jmp", TokenType.JMP),
            Map.entry("je", TokenType.JE),
            Map.entry("print", TokenType.PRINT),
            Map.entry("push", TokenType.PUSH),
            Map.entry("pop", TokenType.POP),

            Map.entry("return", TokenType.RETURN),
            Map.entry("while", TokenType.WHILE),
            Map.entry("for", TokenType.FOR),
            Map.entry("this", TokenType.THIS),
            Map.entry("True", TokenType.TRUE),
            Map.entry("False", TokenType.FALSE),
            Map.entry("null", TokenType.NULL),
            Map.entry("new", TokenType.NEW),
            Map.entry("super", TokenType.SUPER),

            Map.entry("eq", TokenType.EQ),
            Map.entry("ne", TokenType.NE),
            Map.entry("lt", TokenType.LT),
            Map.entry("gt", TokenType.GT),
            Map.entry("le", TokenType.LE),
            Map.entry("ge", TokenType.GE),
            Map.entry("and", TokenType.AND),
            Map.entry("or", TokenType.OR));
    private static final Map<String, TokenType> SYMBOLS = Map.ofEntries(
            Map.entry("{", TokenType.OPEN_BRACE),
            Map.entry("}", TokenType.CLOSE_BRACE),
            Map.entry("(", TokenType.OPEN_PARENTHESIS),
            Map.entry(")", TokenType.CLOSE_PARENTHESIS),
            Map.entry(";", TokenType.SEMICOLON),
            Map.entry(",", TokenType.COMMA),
            Map.entry(".", TokenType.DOT),
            Map.entry(":", TokenType.COLON),
            Map.entry("=", TokenType.ASSIGN),
            Map.entry("\"", TokenType.QUOTE),
            Map.entry("'", TokenType.SINGLE_QUOTE));

    private LexerState state = new InitialState();

    public Lexer(String source) {
        this.source = source;
        this.currentPosition = 0;
    }

    public void changeState(LexerState state) {
        this.state = state;
    }

    public boolean finished() {
        return currentPosition >= source.length();
    }

    public char current() {
        return source.charAt(currentPosition);
    }

    public char advance() {
        char character = source.charAt(currentPosition++);

        if (character == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }

        return character;
    }

    public List<Token> analyze() {
        while (!finished()) {
            state.process(this);

        }

        if (state instanceof StringState) {
            state.process(this);
        }

        tokens.add(new Token(TokenType.EOF, "EOF", line, column));

        return tokens;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public void addToken(Token token) {
        tokens.add(token);
    }

    public TokenType getType(String lexeme) {
        TokenType type = KEYWORDS.get(lexeme);

        if (type != null) {
            return type;
        }

        type = SYMBOLS.get(lexeme);

        if (type != null) {
            return type;
        }

        if (isRegister(lexeme)) {
            return TokenType.REGISTER;
        }

        return TokenType.IDENTIFIER;
    }

    public boolean isSymbol(char character) {
        return SYMBOLS.containsKey(String.valueOf(character));
    }

    private boolean isRegister(String lexeme) {
        return lexeme.matches("R\\d+");
    }
}
