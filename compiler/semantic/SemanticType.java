package compiler.semantic;

import java.util.List;

import compiler.lexer.TokenType;

public record SemanticType(
    TokenType primitiveType,
    String className,
    int arrayDimensions,
    List<SemanticType> typeArguments,
    WildcardKind wildcardKind,
    SemanticType wildcardBound
) {

    public enum WildcardKind {
        NONE,
        UNBOUNDED,
        EXTENDS,
        SUPER
    }

    public SemanticType {
        typeArguments = List.copyOf(typeArguments);
        wildcardKind = wildcardKind == null ? WildcardKind.NONE : wildcardKind;
    }

    public SemanticType(TokenType primitiveType, String className, int arrayDimensions) {
        this(primitiveType, className, arrayDimensions, List.of(), WildcardKind.NONE, null);
    }

    public SemanticType(
            TokenType primitiveType,
            String className,
            int arrayDimensions,
            List<SemanticType> typeArguments) {
        this(primitiveType, className, arrayDimensions, typeArguments, WildcardKind.NONE, null);
    }

    public static SemanticType primitive(TokenType type) {
        return new SemanticType(type, null, 0, List.of(), WildcardKind.NONE, null);
    }

    public static SemanticType classType(String name) {
        return classType(name, List.of());
    }

    public static SemanticType classType(String name, List<SemanticType> typeArguments) {
        return new SemanticType(null, name, 0, typeArguments, WildcardKind.NONE, null);
    }

    public static SemanticType array(
            TokenType primitiveType,
            String className,
            int dimensions) {
        return new SemanticType(primitiveType, className, dimensions, List.of(), WildcardKind.NONE, null);
    }

    public static SemanticType array(
            TokenType primitiveType,
            String className,
            int dimensions,
            List<SemanticType> typeArguments) {
        return new SemanticType(primitiveType, className, dimensions, typeArguments, WildcardKind.NONE, null);
    }

    public static SemanticType nullType() {
        return new SemanticType(TokenType.NULL, null, 0, List.of(), WildcardKind.NONE, null);
    }

    public static SemanticType wildcard(WildcardKind kind, SemanticType bound) {
        return new SemanticType(null, null, 0, List.of(), kind, bound);
    }

    public boolean isWildcard() {
        return wildcardKind != WildcardKind.NONE;
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
