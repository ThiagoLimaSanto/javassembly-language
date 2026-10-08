package src.records;

import src.enums.TokenType;

/** Formatação compartilhada da árvore exibida pelos records. */
final class AstFormat {
    private AstFormat() {
    }

    static String node(String label, Object... children) {
        StringBuilder result = new StringBuilder(label);

        for (int i = 0; i < children.length; i++) {
            boolean last = i == children.length - 1;
            String[] lines = String.valueOf(children[i]).split("\\R", -1);
            result.append('\n').append(last ? "└── " : "├── ").append(lines[0]);

            for (int j = 1; j < lines.length; j++) {
                result.append('\n').append(last ? "    " : "│   ").append(lines[j]);
            }
        }

        return result.toString();
    }

    static String visibility(TokenType visibility) {
        return visibility == null ? "" : " [" + visibility + "]";
    }

    static String literal(Object value) {
        if (value instanceof String text) {
            return "\"" + text.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t") + "\"";
        }
        return String.valueOf(value);
    }
}
