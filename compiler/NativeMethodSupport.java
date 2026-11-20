package compiler;

import java.util.List;

import compiler.ast.records.ClassRecord;
import compiler.ast.records.FunctionRecord;

/** Defines native methods implemented by the compiler runtime. */
public final class NativeMethodSupport {
    private NativeMethodSupport() {
    }

    public static boolean supports(ClassRecord owner, FunctionRecord method) {
        String type = owner.name();
        if (type.equals("io.Scanner") || type.equals("jvs.io.Scanner")) {
            String expectedResult = switch (method.name()) {
                case "readInt" -> "int";
                case "readLong" -> "long";
                case "readDouble" -> "double";
                case "readBoolean" -> "boolean";
                case "readString", "readLine" -> "String";
                default -> null;
            };
            return method.isStatic() && method.parameters().isEmpty()
                    && expectedResult != null
                    && method.returnType().name().equals(expectedResult);
        }

        if (!type.startsWith("jvs.lang.")) return false;
        String simpleName = type.substring("jvs.lang.".length());
        return switch (simpleName) {
            case "Object" ->
                    nativeInstance(method, "hashCode", List.of(), "int")
                    || nativeInstance(method, "toString", List.of(), "String");
            case "String" ->
                    nativeInstance(method, "length", List.of(), "int")
                    || nativeInstance(method, "isEmpty", List.of(), "boolean")
                    || nativeInstance(method, "equals", List.of("jvs.lang.Object"), "boolean")
                    || nativeInstance(method, "hashCode", List.of(), "int")
                    || nativeInstance(method, "toString", List.of(), "String")
                    || nativeInstance(method, "codePointAt", List.of("int"), "int")
                    || nativeInstance(method, "substring", List.of("int"), "String")
                    || nativeInstance(method, "substring", List.of("int", "int"), "String")
                    || nativeInstance(method, "indexOf", List.of("jvs.lang.String"), "int")
                    || nativeInstance(method, "indexOf", List.of("jvs.lang.String", "int"), "int")
                    || nativeInstance(method, "startsWith", List.of("jvs.lang.String"), "boolean")
                    || nativeInstance(method, "endsWith", List.of("jvs.lang.String"), "boolean")
                    || nativeInstance(method, "contains", List.of("jvs.lang.String"), "boolean")
                    || nativeInstance(method, "split", List.of("jvs.lang.String"), "String[]");
            case "Integer" ->
                    nativeInstance(method, "equals", List.of("jvs.lang.Object"), "boolean")
                    || nativeInstance(method, "hashCode", List.of(), "int")
                    || nativeInstance(method, "toString", List.of(), "String")
                    || nativeStatic(method, "parseInt", List.of("jvs.lang.String"), "int")
                    || nativeStatic(method, "parseInt", List.of("jvs.lang.String", "int"), "int");
            case "Long" ->
                    nativeInstance(method, "equals", List.of("jvs.lang.Object"), "boolean")
                    || nativeInstance(method, "hashCode", List.of(), "int")
                    || nativeInstance(method, "toString", List.of(), "String")
                    || nativeStatic(method, "parseLong", List.of("jvs.lang.String"), "long")
                    || nativeStatic(method, "parseLong", List.of("jvs.lang.String", "int"), "long");
            case "Boolean" ->
                    nativeInstance(method, "equals", List.of("jvs.lang.Object"), "boolean")
                    || nativeInstance(method, "hashCode", List.of(), "int")
                    || nativeInstance(method, "toString", List.of(), "String")
                    || nativeStatic(method, "parseBoolean", List.of("jvs.lang.String"), "boolean");
            case "Double" ->
                    nativeInstance(method, "equals", List.of("jvs.lang.Object"), "boolean")
                    || nativeInstance(method, "hashCode", List.of(), "int")
                    || nativeInstance(method, "toString", List.of(), "String")
                    || nativeStatic(method, "parseDouble", List.of("jvs.lang.String"), "double");
            default -> false;
        };
    }

    private static boolean nativeInstance(
            FunctionRecord method, String name, List<String> parameters, String result) {
        return method.name().equals(name) && !method.isStatic()
                && parametersOf(method).equals(parameters)
                && method.returnType().name().equals(result);
    }

    private static boolean nativeStatic(
            FunctionRecord method, String name, List<String> parameters, String result) {
        return method.name().equals(name) && method.isStatic()
                && parametersOf(method).equals(parameters)
                && method.returnType().name().equals(result);
    }

    private static List<String> parametersOf(FunctionRecord method) {
        return method.parameters().stream().map(parameter -> canonicalType(parameter.type().name())).toList();
    }

    private static String canonicalType(String type) {
        return switch (type) {
            case "Object" -> "jvs.lang.Object";
            case "String" -> "jvs.lang.String";
            default -> type;
        };
    }
}
