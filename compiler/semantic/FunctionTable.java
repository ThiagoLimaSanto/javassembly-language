package compiler.semantic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import compiler.ast.records.FunctionRecord;
import compiler.ast.records.ParameterRecord;
import compiler.ast.records.TypeNames;

public class FunctionTable extends SymbolTable<FunctionRecord> {

    private final List<String> classTypeParameters;

    public FunctionTable() {
        this(List.of());
    }

    public FunctionTable(List<String> classTypeParameters) {
        this.classTypeParameters = List.copyOf(classTypeParameters);
    }

    @Override
    protected String getKey(FunctionRecord function) {
        return createSignature(function);
    }

    @Override
    protected int getLine(FunctionRecord function) {
        return function.line();
    }

    @Override
    protected int getColumn(FunctionRecord function) {
        return function.column();
    }

    @Override
    protected String getDuplicateMessage(FunctionRecord function) {
        return "Funcao '" + createSignature(function) + "' ja declarada.";
    }

    private String createSignature(FunctionRecord function) {
        StringBuilder signature = new StringBuilder();

        Map<String, String> erasedTypeParameters = new HashMap<>();
        classTypeParameters.forEach(parameter -> erasedTypeParameters.put(
                parameter, "jvs.lang.Object"));
        function.typeParameters().forEach(parameter -> erasedTypeParameters.put(
                parameter, "jvs.lang.Object"));

        signature.append(function.name()).append("(");

        for (int i = 0; i < function.parameters().size(); i++) {
            ParameterRecord parameter = function.parameters().get(i);

            signature.append(TypeNames.erase(
                    parameter.type().name(), erasedTypeParameters));

            if (i < function.parameters().size() - 1) {
                signature.append(",");
            }
        }

        signature.append(")");

        return signature.toString();
    }

}
