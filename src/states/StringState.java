package src.states;

import src.InitialState;
import src.Lexer;
import src.Token;
import src.enums.TokenType;
import src.exceptions.LexicalException;

public class StringState implements LexerState {
    private final char delimiter;
    private final int line;
    private final int column;
    private final StringBuilder text = new StringBuilder();

    public StringState(char delimiter, int line, int column) {
        this.delimiter = delimiter;
        this.line = line;
        this.column = column;
    }

    @Override
    public void process(Lexer lexer) {
        if (lexer.finished() || lexer.current() == '\n') {
            throw new LexicalException("Faltando delimitador de string.", line, column);
        }

        char character = lexer.advance();

        if (character == delimiter) {
            lexer.addToken(new Token(TokenType.TEXT, text.toString(), line, column));

            lexer.changeState(new InitialState());
            return;
        }

        text.append(character);
    }
}
