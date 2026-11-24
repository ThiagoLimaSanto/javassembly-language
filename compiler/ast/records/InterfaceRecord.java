package compiler.ast.records;

import java.util.List;

import compiler.ast.ASTNode;
import compiler.ast.visitor.ASTVisitor;
import compiler.lexer.TokenType;

public record InterfaceRecord(
    TokenType visibility,
    String name,
    List<String> typeParameters,
    List<String> superInterfaces,
    List<String> genericSuperInterfaceTypes,
    List<FunctionRecord> functions, int line, int column
) implements ASTNode {

    public InterfaceRecord {
        typeParameters = List.copyOf(typeParameters);
        superInterfaces = List.copyOf(superInterfaces);
        genericSuperInterfaceTypes = List.copyOf(genericSuperInterfaceTypes);
        functions = List.copyOf(functions);
    }

    public InterfaceRecord(
        TokenType visibility,
        String name,
        List<String> superInterfaces,
        List<FunctionRecord> functions,
        int line,
        int column
    ) {
        this(visibility, name, List.of(), superInterfaces, superInterfaces, functions, line, column);
    }

    public InterfaceRecord(
        TokenType visibility,
        String name,
        List<FunctionRecord> functions,
        int line,
        int column
    ) {
        this(visibility, name, List.of(), List.of(), List.of(), functions, line, column);
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
