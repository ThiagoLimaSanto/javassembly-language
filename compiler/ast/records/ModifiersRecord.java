package compiler.ast.records;

import compiler.lexer.TokenType;

public record ModifiersRecord(
    TokenType visibility,
    boolean isStatic,
    boolean isFinal,
    boolean isAbstract,
    boolean isNative
) {
    public ModifiersRecord(
        TokenType visibility,
        boolean isStatic,
        boolean isFinal,
        boolean isAbstract
    ) {
        this(visibility, isStatic, isFinal, isAbstract, false);
    }

    public ModifiersRecord(TokenType visibility, boolean isStatic, boolean isFinal) {
        this(visibility, isStatic, isFinal, false, false);
    }
}
