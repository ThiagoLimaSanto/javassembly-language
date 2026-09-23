import java.util.ArrayList;
import java.util.List;

import exceptions.LexicalException;

public class Lexer {

    private String source;
    private int currentPosition;

    public Lexer(String source) {
        this.source = source;
        this.currentPosition = 0;
    }

    public List<Token> analyze() {
        List<Token> tokens = new ArrayList<>();
        int line = 1;
        int column = 1;

        while (currentPosition < source.length()) {

            char atual = source.charAt(currentPosition);

            if (isNewLine(atual)) {
                line++;
                column = 1;
                currentPosition++;
                continue;
            }

            if (isWhitespace(atual)) {
                currentPosition++;
                column++;
                continue;
            }

            int startColumn = column;

            if (isDigit(atual)) {

                String lexema = "";

                while (currentPosition < source.length()
                        && isDigit(source.charAt(currentPosition))) {
                    lexema += source.charAt(currentPosition);
                    column++;
                    currentPosition++;
                }

                tokens.add(new Token(TokenType.NUMBER, lexema, line, startColumn));
                continue;
            }

            if (isQuote(atual) || isSingleQuote(atual)) {
                char delimiter = atual;
                StringBuilder lexema = new StringBuilder();

                currentPosition++;
                column++;

                while (currentPosition < source.length()
                        && source.charAt(currentPosition) != delimiter
                        && !isNewLine(source.charAt(currentPosition))) {
                    lexema.append(source.charAt(currentPosition));
                    currentPosition++;
                    column++;
                }

                if (currentPosition >= source.length()
                        || source.charAt(currentPosition) != delimiter) {
                    throw new LexicalException(
                            "Faltando delimitador de string", line, column);
                }

                currentPosition++;
                column++;

                tokens.add(new Token(
                        TokenType.STRING, lexema.toString(), line, startColumn));
                continue;
            }

            if (isLetter(atual)) {

                String lexema = "";

                while (currentPosition < source.length()
                        && isLetterOrDigit(source.charAt(currentPosition))) {
                    lexema += source.charAt(currentPosition);
                    column++;
                    currentPosition++;
                }

                TokenType type = getType(lexema);

                tokens.add(new Token(type, lexema, line, startColumn));
                continue;
            }

            String lexema = String.valueOf(atual);
            if ((atual == '=' || atual == '!' || atual == '<' || atual == '>')
                    && currentPosition + 1 < source.length()
                    && source.charAt(currentPosition + 1) == '=') {
                lexema += '=';
            }

            TokenType type = getType(lexema);

            tokens.add(new Token(type, lexema, line, startColumn));
            column += lexema.length();
            currentPosition += lexema.length();
        }

        tokens.add(new Token(TokenType.EOF, "EOF", line, column));

        return tokens;
    }

    private TokenType getType(String lexema) {

        switch (lexema) {
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

    private boolean isNewLine(char c) {
        return c == '\n';
    }

    private boolean isQuote(char c) {
        return c == '"';
    }

    private boolean isSingleQuote(char c) {
        return c == '\'';
    }

    private boolean isDigit(char c) {
        return Character.isDigit(c);
    }

    private boolean isLetter(char c) {
        return Character.isLetter(c);
    }

    private boolean isLetterOrDigit(char c) {
        return Character.isLetterOrDigit(c);
    }

    private boolean isWhitespace(char c) {
        return Character.isWhitespace(c);
    }
}
