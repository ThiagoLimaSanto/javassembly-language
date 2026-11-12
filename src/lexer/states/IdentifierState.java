package src.lexer.states;

import src.lexer.Lexer;
import src.lexer.Token;

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

        String lexeme = text.toString();
        lexer.addToken(new Token(lexer.getType(lexeme), lexeme, line, column));

        lexer.changeState(new InitialState());
    }
}
