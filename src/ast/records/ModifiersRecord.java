package src.ast.records;

import src.lexer.TokenType;

public record ModifiersRecord(
    TokenType visibility,
    boolean isStatic,
    boolean isFinal,
    boolean isAbstract
) {
    public ModifiersRecord(TokenType visibility, boolean isStatic, boolean isFinal) {
        this(visibility, isStatic, isFinal, false);
    }
}
