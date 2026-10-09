package src.states;

import src.Lexer;
import src.Token;
import src.enums.TokenType;
import src.exceptions.LexicalException;

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

        while (!lexer.finished() && isDigit(lexer.current())) {
            text.append(lexer.advance());
        }

        if (!lexer.finished() && lexer.current() == '.') {
            text.append(lexer.advance());

            if (lexer.finished() || !isDigit(lexer.current())) {
                throw new LexicalException("Esperado dígito após o ponto decimal",
                        lexer.getLine(),
                        lexer.getColumn());
            }

            while (!lexer.finished()
                    && isDigit(lexer.current())) {
                text.append(lexer.advance());
            }
        }

        lexer.addToken(new Token(TokenType.NUMBER, text.toString(), line, column));
        lexer.changeState(new InitialState());
    }

    private boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }
}
