package src.ast.records;

import java.util.List;

import src.lexer.TokenType;
import src.ast.ASTNode;
import src.ast.visitor.ASTVisitor;

public record FunctionRecord(
        TokenType visibility,
        boolean isStatic,
        boolean isFinal,
        TypeRecord returnType,
        String name,
        List<ParameterRecord> parameters,
        BlockRecord block,
        boolean isAbstract,
        int line,
        int column) implements ASTNode {

    public FunctionRecord {
        parameters = List.copyOf(parameters);
    }

    public FunctionRecord(
            TokenType visibility,
            boolean isStatic,
            boolean isFinal,
            TypeRecord returnType,
            String name,
            List<ParameterRecord> parameters,
            BlockRecord block,
            int line,
            int column) {
        this(visibility, isStatic, isFinal, returnType, name, parameters, block, false, line, column);
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }

}
