package src.semantic;

import java.util.HashMap;
import java.util.Map;

import src.exceptions.SemanticException;

public abstract class SymbolTable<T> {

    protected final Map<String, T> symbols = new HashMap<>();

    protected abstract String getKey(T symbol);

    protected abstract int getLine(T symbol);

    protected abstract int getColumn(T symbol);

    protected abstract String getDuplicateMessage(T symbol);

    public void define(T symbol) {
        String key = getKey(symbol);

        if (symbols.containsKey(key)) {
            throw new SemanticException(
                getDuplicateMessage(symbol),
                getLine(symbol),
                getColumn(symbol)
            );
        }

        symbols.put(key, symbol);
    }

    public T resolve(String key) {
        return symbols.get(key);
    }
}