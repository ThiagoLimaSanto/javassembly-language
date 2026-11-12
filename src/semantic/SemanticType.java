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
        return new SemanticType(null, name);
    }

    public static SemanticType nullType() {
        return new SemanticType(TokenType.NULL, null);
    }

    public boolean isPrimitive() {
        return primitiveType != null && primitiveType != TokenType.NULL;
    }

    public boolean isClass() {
        return className != null;
    }

    public boolean isNull() {
        return primitiveType == TokenType.NULL;
    }
}