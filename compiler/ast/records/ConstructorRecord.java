package compiler.ast.records;

import java.util.List;

import compiler.ast.ASTNode;
import compiler.ast.visitor.ASTVisitor;
import compiler.lexer.TokenType;

public record ConstructorRecord(
        TokenType visibility,
        List<ParameterRecord> parameters,
        List<TypeRecord> thrownExceptions,
        ConstructorInvocationRecord invocation,
        BlockRecord block,
        int line,
        int column) implements ASTNode {

    public ConstructorRecord {
        parameters = List.copyOf(parameters);
        thrownExceptions = List.copyOf(thrownExceptions);
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
