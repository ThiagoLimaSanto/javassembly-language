package compiler.semantic;

import java.util.Map;

public final class BuiltinExceptionTypes {
    private static final Map<String, String> PARENTS = Map.ofEntries(
            Map.entry("Exception", "Throwable"),
            Map.entry("RuntimeException", "Exception"),
            Map.entry("Error", "Throwable"),
            Map.entry("VirtualMachineError", "Error"),
            Map.entry("LinkageError", "Error"),
            Map.entry("IncompatibleClassChangeError", "LinkageError"),
            Map.entry("AbstractMethodError", "IncompatibleClassChangeError"),
            Map.entry("OutOfMemoryError", "VirtualMachineError"),
            Map.entry("StackOverflowError", "VirtualMachineError"),
            Map.entry("NoSuchElementException", "RuntimeException"),
            Map.entry("InputMismatchException", "NoSuchElementException"),
            Map.entry("IOException", "Exception"),
            Map.entry("ArrayStoreException", "RuntimeException"),
            Map.entry("IndexOutOfBoundsException", "RuntimeException"),
            Map.entry("NegativeArraySizeException", "RuntimeException"),
            Map.entry("NullPointerException", "RuntimeException"),
            Map.entry("InvalidReferenceException", "RuntimeException"),
            Map.entry("InvalidFreeException", "RuntimeException"),
            Map.entry("ArithmeticException", "RuntimeException"));

    private BuiltinExceptionTypes() {}

    public static String simpleName(String typeName) {
        int separator = typeName.lastIndexOf('.');
        return separator < 0 ? typeName : typeName.substring(separator + 1);
    }

    public static boolean isBuiltin(String typeName) {
        return simpleName(typeName).equals("Throwable")
                || PARENTS.containsKey(simpleName(typeName));
    }

    public static String parentOf(String typeName) {
        return PARENTS.get(simpleName(typeName));
    }

    public static boolean isSubtype(String typeName, String expectedType) {
        String current = simpleName(typeName);
        String expected = simpleName(expectedType);
        java.util.Set<String> visited = new java.util.HashSet<>();
        while (current != null && visited.add(current)) {
            if (current.equals(expected)) return true;
            current = PARENTS.get(current);
        }
        return false;
    }

    public static java.util.List<String> names() {
        java.util.ArrayList<String> names = new java.util.ArrayList<>(PARENTS.keySet());
        names.add("Throwable");
        names.sort(String::compareTo);
        return java.util.List.copyOf(names);
    }
}
