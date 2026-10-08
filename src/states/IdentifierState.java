package src.states;

import src.InitialState;
import src.Lexer;
import src.Token;

public class IdentifierState implements LexerState {
    private int line;
    private int column;

    public IdentifierState(int line, int column) {
        this.line = line;
        this.column = column;
    }

    @Override
    public void process(Lexer lexer) {
        StringBuilder text = new StringBuilder();

        while (!lexer.finished() && Character.isLetterOrDigit(lexer.current())) {
            text.append(lexer.advance());
        }

        lexer.addToken(new Token(lexer.getType(text.toString()), text.toString(), line, column));

        lexer.changeState(new InitialState());
    }
}
