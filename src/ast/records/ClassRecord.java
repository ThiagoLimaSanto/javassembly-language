package src.ast.records;

import java.util.List;

import src.ast.ASTNode;
import src.ast.visitor.ASTVisitor;
import src.lexer.TokenType;

public record ClassRecord(
        TokenType visibility,
        String name,
        String superClass,
        List<String> interfaces,
        List<AttributeRecord> attributes,
        List<FunctionRecord> functions,
        List<ConstructorRecord> constructors,
        boolean isAbstract,
        int line,
        int column) implements ASTNode {

    public ClassRecord(
            TokenType visibility,
            String name,
            String superClass,
            List<String> interfaces,
            List<AttributeRecord> attributes,
            List<FunctionRecord> functions,
            int line,
            int column) {
        this(visibility, name, superClass, interfaces, attributes, functions, List.of(), false, line, column);
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
