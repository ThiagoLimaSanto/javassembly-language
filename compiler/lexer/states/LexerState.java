package compiler.lexer.states;

import compiler.lexer.Lexer;

public interface LexerState {
    void process(Lexer lexer);
}
