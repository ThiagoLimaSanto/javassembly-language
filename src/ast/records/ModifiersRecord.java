package src.ast.records;

import src.lexer.TokenType;

public record ModifiersRecord(
    TokenType visibility,
    boolean isStatic,
    boolean isFinal
) {
    
}
