package src.semantic;

import src.lexer.TokenType;

public record SemanticType(
    TokenType primitiveType,
    String className,
    int arrayDimensions
) {

    public static SemanticType primitive(TokenType type) {
        return new SemanticType(type, null, 0);
    }

    public static SemanticType classType(String name) {
        return new SemanticType(null, name, 0);
    }

    public static SemanticType array(
            TokenType primitiveType,
            String className,
            int dimensions) {
        return new SemanticType(primitiveType, className, dimensions);
    }

    public static SemanticType nullType() {
        return new SemanticType(TokenType.NULL, null, 0);
    }

    public boolean isPrimitive() {
        return arrayDimensions == 0
                && primitiveType != null
                && primitiveType != TokenType.NULL;
    }

    public boolean isClass() {
        return arrayDimensions == 0 && className != null;
    }

    public boolean isArray() {
        return arrayDimensions > 0;
    }

    public boolean isNull() {
        return primitiveType == TokenType.NULL;
    }
}