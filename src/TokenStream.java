package src;

import java.util.List;

import src.enums.TokenType;
import src.exceptions.SyntacticException;

public class TokenStream {

    private final List<Token> tokens;
    private int position = 0;

    public TokenStream(List<Token> tokens) {
        if (tokens.isEmpty() || tokens.get(tokens.size() - 1).getType() != TokenType.EOF) {
            throw new IllegalArgumentException("Tokens list cannot be empty");
        }

        this.tokens = List.copyOf(tokens);
    }

    public Token current() {
        return tokens.get(position);
    }

    public boolean verify(TokenType type) {
        return current().getType() == type;
    }

    public Token peek() {
        int nextPosition = Math.min(position + 1, tokens.size() - 1);
        return tokens.get(nextPosition);
    }

    public Token consumer(TokenType type, String message) {
        if (!verify(type)) {
            throw new SyntacticException(
                    message
                            + ". Na linha: " + current().getLine()
                            + ", coluna: " + current().getColumn());
        }

        Token token = current();

        if (token.getType() != TokenType.EOF) {
            position++;
        }

        return token;
    }
}
