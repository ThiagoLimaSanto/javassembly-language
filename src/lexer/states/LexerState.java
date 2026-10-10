package src.lexer.states;

import src.lexer.Lexer;

public interface LexerState {
    void process(Lexer lexer);
}
