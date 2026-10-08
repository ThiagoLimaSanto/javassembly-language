package src.states;

import src.Lexer;
import src.Token;
import src.exceptions.LexicalException;

public class InitialState implements LexerState {

    private int line;
    private int column;

    @Override
    public void process(Lexer lexer) {
        setLine(lexer.getLine());
        setColumn(lexer.getColumn());
        char currentCharacter = lexer.current();
        if (Character.isWhitespace(currentCharacter)) {
            lexer.advance();
            return;
        }

        if (Character.isLetter(currentCharacter)) {
            lexer.changeState(new IdentifierState(this.line, this.column));
            return;
        }

        if (Character.isDigit(currentCharacter)) {
            lexer.changeState(new NumberState(this.line, this.column));
            return;
        }

        if (currentCharacter == '"' || currentCharacter == '\'') {
            char delimiter = lexer.advance();

            lexer.changeState(new StringState(delimiter, this.line, this.column));

            return;
        }

        if ("=.,;{}()".indexOf(lexer.current()) >= 0) {
            String lexema = String.valueOf(lexer.advance());
            lexer.addToken(new Token(lexer.getType(lexema), lexema, this.line, this.column));
            return;
        }

        throw new LexicalException(
                "Caractere inválido: " + lexer.current(),
                lexer.getLine(),
                lexer.getColumn());
    }

    private void setLine(int line) {
        this.line = line;
    }

    private void setColumn(int column) {
        this.column = column;
    }
}
