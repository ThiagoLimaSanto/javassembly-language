package compiler.ast.records;

import java.util.List;

import compiler.ast.ASTNode;
import compiler.ast.visitor.ASTVisitor;
import compiler.lexer.TokenType;

public record ClassRecord(
        TokenType visibility,
        String name,
        String superClass,
        List<String> interfaces,
        String genericSuperType,
        List<String> genericInterfaceTypes,
        List<AttributeRecord> attributes,
        List<FunctionRecord> functions,
        List<ConstructorRecord> constructors,
        List<String> typeParameters,
        boolean isAbstract,
        boolean isFinal,
        int line,
        int column) implements ASTNode {

    public ClassRecord {
        interfaces = List.copyOf(interfaces);
        genericInterfaceTypes = List.copyOf(genericInterfaceTypes);
        attributes = List.copyOf(attributes);
        functions = List.copyOf(functions);
        constructors = List.copyOf(constructors);
        typeParameters = List.copyOf(typeParameters);
    }

    public ClassRecord(
            TokenType visibility,
            String name,
            String superClass,
            List<String> interfaces,
            List<AttributeRecord> attributes,
            List<FunctionRecord> functions,
            List<ConstructorRecord> constructors,
            boolean isAbstract,
            boolean isFinal,
            int line,
            int column) {
        this(visibility, name, superClass, interfaces, superClass, interfaces, attributes,
                functions, constructors, List.of(), isAbstract, isFinal, line, column);
    }

    public ClassRecord(
            TokenType visibility,
            String name,
            String superClass,
            List<String> interfaces,
            List<AttributeRecord> attributes,
            List<FunctionRecord> functions,
            List<ConstructorRecord> constructors,
            boolean isAbstract,
            int line,
            int column) {
        this(
                visibility,
                name,
                superClass,
                interfaces,
                attributes,
                functions,
                constructors,
                isAbstract,
                false,
                line,
                column);
    }

    public ClassRecord(
            TokenType visibility,
            String name,
            String superClass,
            List<String> interfaces,
            List<AttributeRecord> attributes,
            List<FunctionRecord> functions,
            int line,
            int column) {
        this(
                visibility,
                name,
                superClass,
                interfaces,
                attributes,
                functions,
                List.of(),
                false,
                false,
                line,
                column);
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
