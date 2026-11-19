package src.backend.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import src.ast.records.ClassRecord;
import src.ast.records.FunctionRecord;
import src.ast.records.ProgramRecord;
import src.lexer.TokenType;

public final class VirtualMethodTable {
    private final Map<String, ClassRecord> classes = new LinkedHashMap<>();
    private final Map<String, Integer> slots = new LinkedHashMap<>();

    public VirtualMethodTable(ProgramRecord program) {
        for (ClassRecord classRecord : program.classes()) {
            classes.put(classRecord.name(), classRecord);
        }
        for (ClassRecord classRecord : program.classes()) {
            for (FunctionRecord function : classRecord.functions()) {
                if (isVirtual(function)) {
                    slots.computeIfAbsent(signature(function), ignored -> slots.size());
                }
            }
        }
    }

    public int slot(FunctionRecord function) {
        Integer slot = slots.get(signature(function));
        if (slot == null) {
            throw new IllegalArgumentException(
                    "Método não possui slot virtual: " + function.name());
        }
        return slot;
    }

    public Map<String, List<String>> tables() {
        Map<String, List<String>> tables = new LinkedHashMap<>();
        for (ClassRecord classRecord : classes.values()) {
            List<String> entries = new ArrayList<>(slots.size());
            for (String signature : slots.keySet()) {
                entries.add(resolveImplementation(classRecord, signature));
            }
            tables.put(classRecord.name(), List.copyOf(entries));
        }
        return Map.copyOf(tables);
    }

    public static boolean isVirtual(FunctionRecord function) {
        return !function.isStatic() && function.visibility() != TokenType.PRIVATE;
    }

    public static String signature(FunctionRecord function) {
        return function.name() + "(" + String.join(",", function.parameters().stream()
                .map(parameter -> parameter.type().name()).toList()) + ")";
    }

    public static String methodLabel(ClassRecord owner, FunctionRecord function) {
        List<String> parameters = function.parameters().stream()
                .map(parameter -> mangleType(parameter.type().name()))
                .toList();
        return owner.name() + "." + function.name() + "__"
                + (parameters.isEmpty() ? "void" : String.join("__", parameters))
                + "__ret_" + mangleType(function.returnType().name());
    }

    private String resolveImplementation(ClassRecord classRecord, String signature) {
        ClassRecord current = classRecord;
        while (current != null) {
            for (FunctionRecord function : current.functions()) {
                if (isVirtual(function) && signature(function).equals(signature)) {
                    return function.isAbstract() ? "0" : methodLabel(current, function);
                }
            }
            current = current.superClass() == null ? null : classes.get(current.superClass());
        }
        return "0";
    }

    private static String mangleType(String type) {
        if (type.equals("String[]"))
            return "String_array";
        if (type.equals("int") || type.equals("long") || type.equals("double")
                || type.equals("boolean") || type.equals("void"))
            return type;
        StringBuilder encoded = new StringBuilder("T");
        type.codePoints().forEach(codePoint -> encoded.append('_')
                .append(Integer.toHexString(codePoint)).append('_'));
        return encoded.toString();
    }
}
