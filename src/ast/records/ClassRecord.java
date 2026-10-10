package src.ast.records;

import java.util.List;

import src.lexer.TokenType;
import src.ast.ASTNode;
import src.ast.visitor.ASTVisitor;

public record ClassRecord(
        TokenType visibility,
        String name,
        List<FunctionRecord> functions) implements ASTNode {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
