package src.semantic;

import java.util.HashMap;
import java.util.Map;

import src.ast.records.VariableSymbolRecord;
import src.exceptions.SemanticException;

public class Scope {

    private final Map<String, VariableSymbolRecord> symbols = new HashMap<>();
    private final Scope parent;

    public Scope(Scope parent) {
        this.parent = parent;
    }

    public void define(VariableSymbolRecord variable) {
        String name = variable.name();

        if (symbols.containsKey(name)) {
            throw new SemanticException(
                    "Variavel '" + name + "' ja declarada.",
                    variable.line(),
                    variable.column());
        }

        symbols.put(name, variable);
    }

    public VariableSymbolRecord resolve(String name) {
        VariableSymbolRecord variable = symbols.get(name);

        if (variable != null) {
            return variable;
        }

        return parent != null ? parent.resolve(name) : null;
    }

    public Scope getParent() {
        return parent;
    }
}