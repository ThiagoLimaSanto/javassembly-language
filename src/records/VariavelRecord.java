package src.records;

import src.interfaces.ExpressionInterface;
import src.interfaces.visitor.ASTVisitor;

public record VariavelRecord(String nome) implements ExpressionInterface {

    @Override
    public String toString() {
        return "Variável: " + nome;
    }

     @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
