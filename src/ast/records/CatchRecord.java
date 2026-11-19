package src.ast.records;

public record CatchRecord(TypeRecord type, String name, BlockRecord block, int line, int column) {}
