package src.records;

import java.util.List;

import src.enums.TokenType;

public record ClassRecord(
        TokenType visibility,
        String name,
        List<FunctionRecord> functions) {

    @Override
    public String toString() {
        return AstFormat.node("Classe " + name + AstFormat.visibility(visibility), functions.toArray());
    }
}
