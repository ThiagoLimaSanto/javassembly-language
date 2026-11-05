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
        List<FunctionRecord> functions, int line, int column) implements ASTNode {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
