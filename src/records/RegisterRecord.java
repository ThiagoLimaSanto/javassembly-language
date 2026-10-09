package src.records;

import src.interfaces.ExpressionInterface;
import src.interfaces.visitor.ASTVisitor;

public record RegisterRecord(String nome) implements ExpressionInterface {

    @Override
    public String toString() {
        return "Registrador: " + nome;
    }

     @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
