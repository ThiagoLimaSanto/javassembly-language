package src.parser;

import java.util.List;
import java.util.Objects;

import src.exceptions.SyntacticException;
import src.lexer.Token;
import src.lexer.TokenType;

public class TokenStream {

    private final List<Token> tokens;
    private int position = 0;

    public TokenStream(List<Token> tokens) {
        Objects.requireNonNull(tokens, "tokens");
        if (tokens.isEmpty() || tokens.get(tokens.size() - 1).getType() != TokenType.EOF) {
            throw new IllegalArgumentException("Tokens list must end with EOF");
        }
        for (int index = 0; index < tokens.size() - 1; index++) {
            if (tokens.get(index).getType() == TokenType.EOF) {
                throw new IllegalArgumentException("EOF can only appear at the end of the token list");
            }
        }

        this.tokens = List.copyOf(tokens);
    }

    public Token current() {
        return tokens.get(position);
    }

    public boolean verify(TokenType type) {
        return current().getType() == type;
    }

    public Token consume(TokenType type, String message) {
        if (!verify(type)) {
            throw new SyntacticException(message, current().getLine(), current().getColumn());
        }

        Token token = current();

        if (token.getType() != TokenType.EOF) {
            position++;
        }

        return token;
    }

    public Token peekNext() {
        return peek(1);
    }

    public Token peek(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Token offset cannot be negative");
        }
        if (offset >= tokens.size() - position) {
            return tokens.get(tokens.size() - 1);
        }
        return tokens.get(position + offset);
    }

}
