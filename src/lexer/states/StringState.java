package src.lexer.states;

import src.lexer.Lexer;
import src.lexer.Token;
import src.lexer.TokenType;
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

        int escapeLine = lexer.getLine();
        int escapeColumn = lexer.getColumn();
        char character = lexer.advance();

        if (character == delimiter) {
            lexer.addToken(new Token(TokenType.TEXT, text.toString(), line, column));

            lexer.changeState(new InitialState());
            return;
        }

        if (character == '\\') {
            if (lexer.finished() || lexer.current() == '\n') {
                throw new LexicalException(
                        "Sequência de escape incompleta.",
                        escapeLine,
                        escapeColumn);
            }

            char escapedCharacter = lexer.advance();
            switch (escapedCharacter) {
                case '\\', '"', '\'' -> text.append(escapedCharacter);
                case 'b' -> text.append('\b');
                case 'f' -> text.append('\f');
                case 'n' -> text.append('\n');
                case 'r' -> text.append('\r');
                case 't' -> text.append('\t');
                default -> throw new LexicalException(
                        "Sequência de escape inválida: \\" + escapedCharacter,
                        escapeLine,
                        escapeColumn);
            }
            return;
        }

        text.append(character);
    }
}
