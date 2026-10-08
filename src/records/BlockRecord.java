package src.records;

import java.util.List;

import src.interfaces.StatementInterface;

public record BlockRecord(List<StatementInterface> commands) implements StatementInterface {

    @Override
    public String toString() {
        return AstFormat.node("Bloco", commands.toArray());
    }
}
