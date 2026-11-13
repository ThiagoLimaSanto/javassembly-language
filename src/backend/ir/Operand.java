package src.backend.ir;

public record Operand(
        Kind kind, Type type, Object value) {
    public enum Kind {
        VARIABLE,
        TEMPORARY,
        CONSTANT,
        LABEL
    }

    public enum Type {
        I32,
        BOOL,
        F64,
        REF
    }

    public static Operand variable(String name, Type type) {
        return new Operand(Kind.VARIABLE, type, name);
    }

    public static Operand temporary(String name, Type type) {
        return new Operand(Kind.TEMPORARY, type, name);
    }

    public static Operand constant(Object value, Type type) {
        return new Operand(Kind.CONSTANT, type, value);
    }

    public static Operand label(String name) {
        return new Operand(Kind.LABEL, null, name);
    }
}
