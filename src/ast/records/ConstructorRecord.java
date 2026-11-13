package src.ast.records;

import java.util.List;

import src.ast.ASTNode;
import src.ast.visitor.ASTVisitor;
import src.lexer.TokenType;

public record ConstructorRecord(
        TokenType visibility,
        List<ParameterRecord> parameters,
        ConstructorInvocationRecord invocation,
        BlockRecord block,
        int line,
        int column) implements ASTNode {

    public ConstructorRecord {
        parameters = List.copyOf(parameters);
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
