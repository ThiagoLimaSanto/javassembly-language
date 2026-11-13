package src.lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import src.lexer.states.InitialState;
import src.lexer.states.LexerState;
import src.lexer.states.StringState;

public class Lexer {

    private final String source;
    private int currentPosition;
    private int line = 1;
    private int column = 1;
    private boolean previousCharacterWasCarriageReturn;
    private final List<Token> tokens = new ArrayList<>();
    private List<Token> result;
    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("class", TokenType.CLASS),
            Map.entry("interface", TokenType.INTERFACE),
            Map.entry("abstract", TokenType.ABSTRACT),
            Map.entry("default", TokenType.DEFAULT),
            Map.entry("package", TokenType.PACKAGE),
            Map.entry("import", TokenType.IMPORT),
            Map.entry("extends", TokenType.EXTENDS),
            Map.entry("implements", TokenType.IMPLEMENTS),
            Map.entry("func", TokenType.FUNC),
            Map.entry("public", TokenType.PUBLIC),
            Map.entry("static", TokenType.STATIC),
            Map.entry("final", TokenType.FINAL),
            Map.entry("private", TokenType.PRIVATE),
            Map.entry("protected", TokenType.PROTECTED),
            Map.entry("int", TokenType.INT),
            Map.entry("long", TokenType.LONG),
            Map.entry("double", TokenType.DOUBLE),
            Map.entry("boolean", TokenType.BOOLEAN),
            Map.entry("String", TokenType.STRING),
            Map.entry("void", TokenType.VOID),
            Map.entry("if", TokenType.IF),
            Map.entry("else", TokenType.ELSE),

            Map.entry("add", TokenType.ADD),
            Map.entry("sub", TokenType.SUB),
            Map.entry("mul", TokenType.MUL),
            Map.entry("div", TokenType.DIV),
            Map.entry("mod", TokenType.MOD),
            Map.entry("neg", TokenType.NEG),
            Map.entry("mov", TokenType.MOV),
            Map.entry("cmp", TokenType.CMP),
            Map.entry("print", TokenType.PRINT),

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
            Map.entry("[", TokenType.OPEN_BRACKET),
            Map.entry("]", TokenType.CLOSE_BRACKET),
            Map.entry("(", TokenType.OPEN_PARENTHESIS),
            Map.entry(")", TokenType.CLOSE_PARENTHESIS),
            Map.entry(";", TokenType.SEMICOLON),
            Map.entry(",", TokenType.COMMA),
            Map.entry(".", TokenType.DOT),
            Map.entry("=", TokenType.ASSIGN),
            Map.entry("\"", TokenType.QUOTE),
            Map.entry("*", TokenType.MUL));

    private LexerState state = new InitialState();

    public Lexer(String source) {
        this.source = java.util.Objects.requireNonNull(source, "source");
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

    public boolean nextIs(char character) {
        return currentPosition + 1 < source.length()
                && source.charAt(currentPosition + 1) == character;
    }

    public boolean nextIsDigit() {
        return currentPosition + 1 < source.length()
                && source.charAt(currentPosition + 1) >= '0'
                && source.charAt(currentPosition + 1) <= '9';
    }

    public boolean isLineTerminator(char character) {
        return character == '\n' || character == '\r';
    }

    public char advance() {
        char character = source.charAt(currentPosition++);

        if (character == '\r') {
            line++;
            column = 1;
            previousCharacterWasCarriageReturn = true;
        } else if (character == '\n') {
            if (!previousCharacterWasCarriageReturn) {
                line++;
            }
            column = 1;
            previousCharacterWasCarriageReturn = false;
        } else {
            column++;
            previousCharacterWasCarriageReturn = false;
        }

        return character;
    }

    public List<Token> analyze() {
        if (result != null) {
            return result;
        }

        while (!finished()) {
            state.process(this);

        }

        if (state instanceof StringState) {
            state.process(this);
        }

        tokens.add(new Token(TokenType.EOF, "EOF", line, column));
        result = List.copyOf(tokens);
        return result;
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
        return switch (character) {
            case '{', '}', '[', ']', '(', ')', ';', ',', '.', '=', '"', '*' -> true;
            default -> false;
        };
    }

    private boolean isRegister(String lexeme) {
        if (lexeme.length() < 2 || lexeme.charAt(0) != 'R') {
            return false;
        }
        for (int index = 1; index < lexeme.length(); index++) {
            char character = lexeme.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }
}
