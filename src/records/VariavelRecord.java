package src.records;

import src.interfaces.ExpressionInterface;

public record VariavelRecord(String nome) implements ExpressionInterface {

    @Override
    public String toString() {
        return "Variável: " + nome;
    }
}
