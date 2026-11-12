package src.semantic;

import src.ast.records.VariableSymbolRecord;

public class VariableTable extends SymbolTable<VariableSymbolRecord> {

    private final VariableTable parent;

    public VariableTable(VariableTable parent) {
        this.parent = parent;
    }

    @Override
    protected String getKey(VariableSymbolRecord variable) {
        return variable.name();
    }

    @Override
    protected int getLine(VariableSymbolRecord variable) {
        return variable.line();
    }

    @Override
    protected int getColumn(VariableSymbolRecord variable) {
        return variable.column();
    }

    @Override
    protected String getDuplicateMessage(VariableSymbolRecord variable) {
        return "Variavel '" + variable.name() + "' ja declarada.";
    }

    public VariableSymbolRecord resolveCurrent(String name) {
        return symbols.get(name);
    }

    @Override
    public VariableSymbolRecord resolve(String name) {
        VariableSymbolRecord variable = super.resolve(name);

        if (variable != null) {
            return variable;
        }

        return parent != null ? parent.resolve(name) : null;
    }

    public VariableTable getParent() {
        return parent;
    }
}