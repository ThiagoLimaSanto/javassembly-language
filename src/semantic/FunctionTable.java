package src.semantic;

import src.ast.records.FunctionRecord;
import src.ast.records.ParameterRecord;

public class FunctionTable extends SymbolTable<FunctionRecord> {

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

        signature.append(function.name()).append("(");

        for (int i = 0; i < function.parameters().size(); i++) {
            ParameterRecord parameter = function.parameters().get(i);

            signature.append(parameter.type().name());

            if (i < function.parameters().size() - 1) {
                signature.append(",");
            }
        }

        signature.append(")");

        return signature.toString();
    }

}