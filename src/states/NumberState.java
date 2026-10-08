package src.states;

import src.Lexer;
import src.Token;
import src.enums.TokenType;

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

        while (!lexer.finished() && Character.isDigit(lexer.current())) {
            text.append(lexer.advance());
        }

        lexer.addToken(new Token(TokenType.NUMBER, text.toString(), line, column));
        lexer.changeState(new InitialState());
    }
}
