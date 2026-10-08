package src.records;

import src.enums.TokenType;

public record FunctionRecord(
                TokenType visibility,
                TokenType returnType,
                String name,
                BlockRecord block) {

    @Override
    public String toString() {
        return AstFormat.node("Função " + name + " → " + returnType + AstFormat.visibility(visibility), block);
    }
}
