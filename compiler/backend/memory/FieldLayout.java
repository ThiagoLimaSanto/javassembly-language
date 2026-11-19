package compiler.backend.memory;

public record FieldLayout(
    String name,
    String type,
    int offSet,
    int size,
    int alignment
) {
}
