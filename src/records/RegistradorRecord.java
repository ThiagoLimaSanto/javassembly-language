package src.records;

import src.interfaces.ExpressionInterface;

public record RegistradorRecord(String nome) implements ExpressionInterface {

    @Override
    public String toString() {
        return "Registrador: " + nome;
    }
}
