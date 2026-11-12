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

        if (!lexer.finished() && lexer.current() == '.') {
            text.append(lexer.advance());
            while (!lexer.finished() && isDigit(lexer.current())) {
                text.append(lexer.advance());
            }
        } else {
            while (!lexer.finished() && isDigit(lexer.current())) {
                text.append(lexer.advance());
            }

            if (!lexer.finished() && lexer.current() == '.') {
                text.append(lexer.advance());
                while (!lexer.finished() && isDigit(lexer.current())) {
                    text.append(lexer.advance());
                }
            }
        }

        if (!lexer.finished() && (lexer.current() == 'e' || lexer.current() == 'E')) {
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

        if (!lexer.finished()
                && (Character.isLetter(lexer.current()) || lexer.current() == '_')) {
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
