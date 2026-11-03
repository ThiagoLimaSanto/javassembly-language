package src.ast.records;

import src.lexer.TokenType;

public record VariableSymbolRecord(String name, TokenType type, int line, int column) {
}
