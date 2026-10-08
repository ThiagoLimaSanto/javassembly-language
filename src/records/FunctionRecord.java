package src.records;

import java.util.List;

import src.enums.TokenType;
import src.interfaces.visitor.ASTNode;
import src.interfaces.visitor.ASTVisitor;

public record FunctionRecord(
        TokenType visibility,
        TokenType returnType,
        String name,
        List<ParameterRecord> parameters,
        BlockRecord block) implements ASTNode {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return AstFormat.node("Função " + name + " → " + returnType + AstFormat.visibility(visibility), block);
    }

}
