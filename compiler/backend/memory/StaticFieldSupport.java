package compiler.backend.memory;

public final class StaticFieldSupport {
    private StaticFieldSupport() {
    }

    public static String label(String owner, String field) {
        return "__static_" + encode(owner) + "_" + encode(field);
    }

    public static int sizeOf(String type) {
        return switch (type) {
            case "boolean", "char" -> 1;
            case "int", "float" -> 4;
            case "long", "double" -> 8;
            default -> 8;
        };
    }

    private static String encode(String value) {
        StringBuilder result = new StringBuilder();
        value.codePoints().forEach(codePoint -> result.append('_')
                .append(Integer.toHexString(codePoint)).append('_'));
        return result.toString();
    }
}
