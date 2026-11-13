package src.ast.records;

import java.util.List;
import src.ast.ASTNode;
import src.ast.visitor.ASTVisitor;
import src.lexer.TokenType;

public record InterfaceRecord(
    TokenType visibility,
    String name,
    List<String> superInterfaces,
    List<FunctionRecord> functions, int line, int column
) implements ASTNode {

    public InterfaceRecord {
        superInterfaces = List.copyOf(superInterfaces);
        functions = List.copyOf(functions);
    }

    public InterfaceRecord(
        TokenType visibility,
        String name,
        List<FunctionRecord> functions,
        int line,
        int column
    ) {
        this(visibility, name, List.of(), functions, line, column);
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
