package src;

import java.util.ArrayList;
import java.util.List;

import src.enums.TokenType;
import src.states.LexerState;
import src.states.StringState;

public class Lexer {

    private String source;
    private int currentPosition;
    private int line = 1;
    private int column = 1;
    private List<Token> tokens = new ArrayList<>();
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

    public TokenType getType(String lexema) {

        switch (lexema) {
            case "String":
                return TokenType.STRING;
            case "add":
                return TokenType.ADD;
            case "sub":
                return TokenType.SUB;
            case "mul":
                return TokenType.MUL;
            case "div":
                return TokenType.DIV;
            case "mov":
                return TokenType.MOV;
            case "cmp":
                return TokenType.CMP;
            case "jmp":
                return TokenType.JMP;
            case "print":
                return TokenType.PRINT;
            case "push":
                return TokenType.PUSH;
            case "pop":
                return TokenType.POP;

            case "return":
                return TokenType.RETURN;
            case "while":
                return TokenType.WHILE;
            case "for":
                return TokenType.FOR;
            case "class":
                return TokenType.CLASS;
            case "this":
                return TokenType.THIS;
            case "true":
                return TokenType.TRUE;
            case "false":
                return TokenType.FALSE;
            case "null":
                return TokenType.NULL;
            case "new":
                return TokenType.NEW;
            case "super":
                return TokenType.SUPER;
            case "public":
                return TokenType.PUBLIC;
            case "private":
                return TokenType.PRIVATE;
            case "double":
                return TokenType.DOUBLE;
            case "boolean":
                return TokenType.BOOLEAN;
            case "void":
                return TokenType.VOID;
            case "int":
                return TokenType.INT;

            case "func":
                return TokenType.FUNC;
            case "main":
                return TokenType.MAIN;

            case "{":
                return TokenType.OPEN_BRACE;
            case "}":
                return TokenType.CLOSE_BRACE;
            case "(":
                return TokenType.OPEN_PARENTHESIS;
            case ")":
                return TokenType.CLOSE_PARENTHESIS;
            case ";":
                return TokenType.SEMICOLON;
            case ",":
                return TokenType.COMMA;
            case ".":
                return TokenType.DOT;
            case "\"":
                return TokenType.QUOTE;
            case "'":
                return TokenType.SINGLE_QUOTE;

            case "eq":
                return TokenType.EQ;

            case "ne":
                return TokenType.NE;

            case "lt":
                return TokenType.LT;

            case "gt":
                return TokenType.GT;

            case "le":
                return TokenType.LE;

            case "ge":
                return TokenType.GE;

            case "and":
                return TokenType.AND;
            case "or":
                return TokenType.OR;
            case "=":
                return TokenType.ASSIGN;

            default:
                if (isRegister(lexema)) {
                    return TokenType.REGISTER;
                }
                return TokenType.IDENTIFIER;
        }
    }

    private boolean isRegister(String lexema) {
        return lexema.matches("R\\d+");
    }
}
