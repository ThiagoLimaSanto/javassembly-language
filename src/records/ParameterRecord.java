package src.records;

import src.enums.TokenType;
import src.interfaces.visitor.ASTNode;
import src.interfaces.visitor.ASTVisitor;

public record ParameterRecord(
        TokenType type,
        String name) implements ASTNode {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}