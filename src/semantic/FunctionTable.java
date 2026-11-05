package src.semantic;

import java.util.HashMap;
import java.util.Map;

import src.ast.records.FunctionRecord;
import src.ast.records.ParameterRecord;
import src.exceptions.SemanticException;

public class FunctionTable {

    private final Map<String, FunctionRecord> functions = new HashMap<>();

    public void define(FunctionRecord function) {

        if (function.name().equals("main")) {

            for (FunctionRecord existing : functions.values()) {
                if (existing.name().equals("main")) {
                    throw new SemanticException(
                            "A funcao 'main' deve ser unica.",
                            function.line(),
                            function.column());
                }
            }
        }

        String signature = createSignature(function);

        if (functions.containsKey(signature)) {
            throw new SemanticException(
                    "Funcao '" + signature + "' ja declarada.",
                    function.line(),
                    function.column());
        }

        functions.put(signature, function);
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