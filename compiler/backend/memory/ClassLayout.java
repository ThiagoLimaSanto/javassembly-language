package compiler.backend.memory;

import java.util.List;

public record ClassLayout(
        String className,
        List<FieldLayout> fields,
        int size,
        int alignment) {

    public ClassLayout {
        fields = List.copyOf(fields);
    }

    public FieldLayout getField(String name) {
        for (int index = fields.size() - 1; index >= 0; index--) {
            FieldLayout field = fields.get(index);
            if (field.name().equals(name)) {
                return field;
            }
        }
        return null;
    }
}
