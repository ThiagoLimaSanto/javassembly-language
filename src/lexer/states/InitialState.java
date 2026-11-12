package src.lexer.states;

import src.exceptions.LexicalException;
import src.lexer.Lexer;
import src.lexer.Token;

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

        if (Character.isLetter(currentCharacter) || currentCharacter == '_') {
            lexer.changeState(new IdentifierState(this.line, this.column));
            return;
        }

        if (currentCharacter >= '0' && currentCharacter <= '9') {
            lexer.changeState(new NumberState(this.line, this.column));
            return;
        }

        if (currentCharacter == '.' && lexer.nextIsDigit()) {
            lexer.changeState(new NumberState(this.line, this.column));
            return;
        }

        if (currentCharacter == '"' || currentCharacter == '\'') {
            char delimiter = lexer.advance();

            lexer.changeState(new StringState(delimiter, this.line, this.column));

            return;
        }

        if (currentCharacter == '/' && lexer.nextIs('/')) {
            lexer.advance();
            lexer.advance();
            while (!lexer.finished()
                    && !lexer.isLineTerminator(lexer.current())) {
                lexer.advance();
            }
            return;
        }

        if (currentCharacter == '/' && lexer.nextIs('*')) {
            lexer.advance();
            lexer.advance();
            while (!lexer.finished()) {
                if (lexer.current() == '*' && lexer.nextIs('/')) {
                    lexer.advance();
                    lexer.advance();
                    return;
                }
                lexer.advance();
            }
            throw new LexicalException(
                    "Faltando delimitador de comentário.",
                    this.line,
                    this.column);
        }

        if (lexer.isSymbol(currentCharacter)) {
            String lexeme = String.valueOf(lexer.advance());

            lexer.addToken(new Token(lexer.getType(lexeme), lexeme, this.line, this.column));

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
