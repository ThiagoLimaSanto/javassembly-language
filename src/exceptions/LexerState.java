package src.exceptions;

import src.Lexer;

public interface LexerState {
    void process(Lexer lexer);
}
