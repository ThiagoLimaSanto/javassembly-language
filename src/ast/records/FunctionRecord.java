package src.ast.records;

import java.util.List;

import src.lexer.TokenType;
import src.ast.ASTNode;
import src.ast.visitor.ASTVisitor;

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

}
