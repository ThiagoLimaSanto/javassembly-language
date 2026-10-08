package src.records;

import java.util.List;

public record ProgramRecord(List<ClassRecord> classes) {

    @Override
    public String toString() {
        return AstFormat.node("Programa", classes.toArray());
    }
}
