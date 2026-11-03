package src.ast.records;

import src.lexer.TokenType;
import src.ast.ASTNode;
import src.ast.visitor.ASTVisitor;

public record ParameterRecord(
        TokenType type,
        String name, int line, int column) implements ASTNode {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}