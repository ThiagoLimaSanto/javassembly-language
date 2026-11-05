package src.semantic;

import src.lexer.TokenType;

public record SemanticType(
    TokenType primitiveType,
    String className
) {

    public static SemanticType primitive(TokenType type) {
        return new SemanticType(type, null);
    }

    public static SemanticType classType(String name) {
        return new SemanticType(null, name.toUpperCase());
    }

    public boolean isPrimitive() {
        return primitiveType != null;
    }

    public boolean isClass() {
        return className != null;
    }
}