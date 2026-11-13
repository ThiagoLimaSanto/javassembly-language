package src.parser;

import java.util.List;

import src.exceptions.InvalidTokenStreamException;
import src.exceptions.SyntacticException;
import src.lexer.Token;
import src.lexer.TokenType;

public class TokenStream {

    private final List<Token> tokens;
    private int position = 0;

    public TokenStream(List<Token> tokens) {
        if (tokens == null) {
            throw new InvalidTokenStreamException("A lista de tokens não pode ser nula.");
        }
        if (tokens.isEmpty()) {
            throw new InvalidTokenStreamException(
                    "A sequência de tokens está vazia; era esperado ao menos o token EOF.");
        }
        for (int index = 0; index < tokens.size(); index++) {
            if (tokens.get(index) == null) {
                throw new InvalidTokenStreamException(
                        "A sequência de tokens contém um valor nulo na posição " + index + ".");
            }
        }
        if (tokens.get(tokens.size() - 1).getType() != TokenType.EOF) {
            throw new InvalidTokenStreamException(
                    "A sequência de tokens está incompleta: o último token precisa ser EOF.");
        }
        for (int index = 0; index < tokens.size() - 1; index++) {
            if (tokens.get(index).getType() == TokenType.EOF) {
                throw new InvalidTokenStreamException(
                        "A sequência de tokens está inconsistente: EOF só pode aparecer no final (posição "
                                + index + ").");
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
            Token found = current();
            String detail = found.getType() == TokenType.EOF
                    ? "Encontrado o fim do arquivo (EOF)."
                    : "Encontrado " + found.getType() + " ('" + printable(found.getLexeme()) + "').";
            throw new SyntacticException(
                    message + (message.endsWith(".") ? " " : ". ") + detail,
                    found.getLine(),
                    found.getColumn());
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
            throw new InvalidTokenStreamException(
                    "O deslocamento para consultar tokens não pode ser negativo: " + offset + ".");
        }
        if (offset >= tokens.size() - position) {
            return tokens.get(tokens.size() - 1);
        }
        return tokens.get(position + offset);
    }

    private String printable(String value) {
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("'", "\\'");
    }

}
