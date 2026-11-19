package compiler.ast.records;

import compiler.semantic.SemanticType;

public record VariableSymbolRecord(String name, SemanticType type, int line, int column) {
}
