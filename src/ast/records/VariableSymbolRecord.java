package src.ast.records;

import src.semantic.SemanticType;

public record VariableSymbolRecord(String name, SemanticType type, int line, int column) {
}
