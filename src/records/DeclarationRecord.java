package src.records;

import src.enums.TokenType;
import src.interfaces.ExpressionInterface;
import src.interfaces.StatementInterface;
import src.interfaces.visitor.ASTVisitor;

public record DeclarationRecord(
        TokenType type,
        String name,
        ExpressionInterface value) implements StatementInterface {

    @Override
    public String toString() {
        return AstFormat.node("Declaração: " + type + " " + name,
                value == null ? "Sem inicialização" : value);
    }

     @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
