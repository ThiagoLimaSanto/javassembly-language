package compiler.ast.records;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Helpers for the compiler's canonical, source-level type-name representation. */
public final class TypeNames {
    private TypeNames() {
    }

    public static String rawName(String typeName) {
        if (typeName == null) return null;
        int genericStart = typeName.indexOf('<');
        return genericStart < 0 ? typeName : typeName.substring(0, genericStart);
    }

    public static List<String> arguments(String typeName) {
        int genericStart = typeName.indexOf('<');
        if (genericStart < 0 || !typeName.endsWith(">")) {
            return List.of();
        }

        List<String> arguments = new ArrayList<>();
        int depth = 0;
        int start = genericStart + 1;
        if (start == typeName.length() - 1) {
            return List.of();
        }
        for (int index = start; index < typeName.length() - 1; index++) {
            char character = typeName.charAt(index);
            if (character == '<') {
                depth++;
            } else if (character == '>') {
                depth--;
            } else if (character == ',' && depth == 0) {
                arguments.add(typeName.substring(start, index));
                start = index + 1;
            }
        }
        arguments.add(typeName.substring(start, typeName.length() - 1));
        return List.copyOf(arguments);
    }

    public static String substitute(String typeName, Map<String, String> substitutions) {
        if (typeName.equals("?")) return typeName;
        if (typeName.startsWith("?extends:")) {
            return "?extends:" + substitute(typeName.substring("?extends:".length()), substitutions);
        }
        if (typeName.startsWith("?super:")) {
            return "?super:" + substitute(typeName.substring("?super:".length()), substitutions);
        }
        int dimensions = 0;
        String base = typeName;
        while (base.endsWith("[]")) {
            dimensions++;
            base = base.substring(0, base.length() - 2);
        }

        String replacement = substitutions.get(base);
        if (replacement != null) {
            return replacement + "[]".repeat(dimensions);
        }

        List<String> arguments = arguments(base);
        if (!arguments.isEmpty()) {
            String rawName = rawName(base);
            base = rawName + "<" + arguments.stream()
                    .map(argument -> substitute(argument, substitutions))
                    .collect(java.util.stream.Collectors.joining(",")) + ">";
        }
        return base + "[]".repeat(dimensions);
    }

    public static String erase(String typeName, Map<String, String> substitutions) {
        int dimensions = 0;
        String base = typeName;
        while (base.endsWith("[]")) {
            dimensions++;
            base = base.substring(0, base.length() - 2);
        }
        String replacement = substitutions.get(base);
        if (replacement != null) {
            base = rawName(replacement);
        } else {
            base = rawName(base);
        }
        return base + "[]".repeat(dimensions);
    }
}
