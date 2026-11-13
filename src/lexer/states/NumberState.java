package src.lexer.states;

import src.exceptions.LexicalException;
import src.lexer.Lexer;
import src.lexer.Token;
import src.lexer.TokenType;

public class NumberState implements LexerState {
    private final int line;
    private final int column;

    public NumberState(int line, int column) {
        this.line = line;
        this.column = column;
    }

    @Override
    public void process(Lexer lexer) {
        StringBuilder text = new StringBuilder();
        boolean hasFraction = false;
        boolean hasExponent = false;
        boolean hasLongSuffix = false;

        if (!lexer.finished() && lexer.current() == '.') {
            hasFraction = true;
            text.append(lexer.advance());
            while (!lexer.finished() && isDigit(lexer.current())) {
                text.append(lexer.advance());
            }
        } else {
            while (!lexer.finished() && isDigit(lexer.current())) {
                text.append(lexer.advance());
            }

            if (!lexer.finished() && lexer.current() == '.') {
                hasFraction = true;
                text.append(lexer.advance());
                while (!lexer.finished() && isDigit(lexer.current())) {
                    text.append(lexer.advance());
                }
            }
        }

        if (!lexer.finished() && (lexer.current() == 'e' || lexer.current() == 'E')) {
            hasExponent = true;
            text.append(lexer.advance());
            if (!lexer.finished() && (lexer.current() == '+' || lexer.current() == '-')) {
                text.append(lexer.advance());
            }

            if (lexer.finished() || !isDigit(lexer.current())) {
                throw new LexicalException(
                        "Esperado dígito no expoente do número.",
                        line,
                        column);
            }

            while (!lexer.finished() && isDigit(lexer.current())) {
                text.append(lexer.advance());
            }
        }

        if (!lexer.finished() && (lexer.current() == 'L' || lexer.current() == 'l')) {
            if (hasFraction || hasExponent) {
                throw new LexicalException(
                        "O sufixo 'L' só pode ser usado em um literal inteiro.",
                        lexer.getLine(),
                        lexer.getColumn());
            }
            text.append(lexer.advance());
            hasLongSuffix = true;
        }

        if (!lexer.finished()
                && (Character.isLetter(lexer.current())
                        || lexer.current() == '_'
                        || (hasLongSuffix && isDigit(lexer.current())))) {
            throw new LexicalException(
                    "Um literal numérico não pode ser seguido diretamente por um identificador.",
                    lexer.getLine(),
                    lexer.getColumn());
        }

        lexer.addToken(new Token(TokenType.NUMBER, text.toString(), line, column));
        lexer.changeState(new InitialState());
    }

    private boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }
}
