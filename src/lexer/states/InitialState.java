package src.lexer.states;

import src.exceptions.LexicalException;
import src.lexer.Lexer;
import src.lexer.Token;

public class InitialState implements LexerState {

    @Override
    public void process(Lexer lexer) {
        int line = lexer.getLine();
        int column = lexer.getColumn();
        char currentCharacter = lexer.current();
        if (Character.isWhitespace(currentCharacter)) {
            lexer.advance();
            return;
        }

        if (Character.isLetter(currentCharacter) || currentCharacter == '_') {
            lexer.changeState(new IdentifierState(line, column));
            return;
        }

        if (currentCharacter >= '0' && currentCharacter <= '9') {
            lexer.changeState(new NumberState(line, column));
            return;
        }

        if (currentCharacter == '.' && lexer.nextIsDigit()) {
            lexer.changeState(new NumberState(line, column));
            return;
        }

        if (currentCharacter == '"') {
            lexer.advance();
            lexer.changeState(new StringState('"', line, column));

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
                    line,
                    column);
        }

        if (lexer.isSymbol(currentCharacter)) {
            String lexeme = String.valueOf(lexer.advance());

            lexer.addToken(new Token(lexer.getType(lexeme), lexeme, line, column));

            return;
        }

        throw new LexicalException(
                "Caractere inválido: " + lexer.current(),
                lexer.getLine(),
                lexer.getColumn());
    }

}
