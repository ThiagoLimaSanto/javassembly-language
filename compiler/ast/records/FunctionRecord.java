package compiler.ast.records;

import java.util.List;

import compiler.ast.ASTNode;
import compiler.ast.visitor.ASTVisitor;
import compiler.lexer.TokenType;

public record FunctionRecord(
        TokenType visibility,
        boolean isStatic,
        boolean isFinal,
        TypeRecord returnType,
        String name,
        List<ParameterRecord> parameters,
        List<TypeRecord> thrownExceptions,
        BlockRecord block,
        boolean isAbstract,
        boolean isDefault,
        boolean isNative,
        int line,
        int column,
        List<String> typeParameters) implements ASTNode {

    public FunctionRecord {
        parameters = List.copyOf(parameters);
        thrownExceptions = List.copyOf(thrownExceptions);
        typeParameters = List.copyOf(typeParameters);
    }

    public FunctionRecord(
            TokenType visibility,
            boolean isStatic,
            boolean isFinal,
            TypeRecord returnType,
            String name,
            List<ParameterRecord> parameters,
            List<TypeRecord> thrownExceptions,
            BlockRecord block,
            boolean isAbstract,
            boolean isDefault,
            boolean isNative,
            int line,
            int column) {
        this(visibility, isStatic, isFinal, returnType, name, parameters, thrownExceptions,
                block, isAbstract, isDefault, isNative, line, column, List.of());
    }

    public FunctionRecord(
            TokenType visibility, boolean isStatic, boolean isFinal,
            TypeRecord returnType, String name, List<ParameterRecord> parameters,
            BlockRecord block, boolean isAbstract, boolean isDefault, boolean isNative,
            int line, int column) {
        this(visibility, isStatic, isFinal, returnType, name, parameters, List.of(),
                block, isAbstract, isDefault, isNative, line, column);
    }

    public FunctionRecord(
            TokenType visibility, boolean isStatic, boolean isFinal,
            TypeRecord returnType, String name, List<ParameterRecord> parameters,
            List<TypeRecord> thrownExceptions, BlockRecord block, int line, int column) {
        this(visibility, isStatic, isFinal, returnType, name, parameters, thrownExceptions,
                block, false, false, false, line, column);
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
        this(visibility, isStatic, isFinal, returnType, name, parameters, List.of(), block,
                false, false, false, line, column);
    }

    public FunctionRecord(
            TokenType visibility,
            boolean isStatic,
            boolean isFinal,
            TypeRecord returnType,
            String name,
            List<ParameterRecord> parameters,
            BlockRecord block,
            boolean isAbstract,
            int line,
            int column) {
        this(visibility, isStatic, isFinal, returnType, name, parameters, List.of(), block,
                isAbstract, false, false, line, column);
    }

    public FunctionRecord(
            TokenType visibility,
            boolean isStatic,
            boolean isFinal,
            TypeRecord returnType,
            String name,
            List<ParameterRecord> parameters,
            BlockRecord block,
            boolean isAbstract,
            boolean isDefault,
            int line,
            int column) {
        this(visibility, isStatic, isFinal, returnType, name, parameters, List.of(), block,
                isAbstract, isDefault, false, line, column);
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }

}
