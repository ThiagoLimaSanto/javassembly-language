package src.backend.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import src.ast.records.ClassRecord;
import src.ast.records.FunctionRecord;
import src.ast.records.InterfaceRecord;
import src.ast.records.ProgramRecord;
import src.lexer.TokenType;
import src.exceptions.InvalidIRException;

public final class VirtualMethodTable {
    private record InterfaceMethod(String owner, FunctionRecord function) {
    }

    private final Map<String, ClassRecord> classes = new LinkedHashMap<>();
    private final Map<String, InterfaceRecord> interfaces = new LinkedHashMap<>();
    private final Map<String, Integer> slots = new LinkedHashMap<>();

    public VirtualMethodTable(ProgramRecord program) {
        for (ClassRecord classRecord : program.classes()) {
            classes.put(classRecord.name(), classRecord);
        }
        for (InterfaceRecord interfaceRecord : program.interfaces()) {
            interfaces.put(interfaceRecord.name(), interfaceRecord);
        }
        for (ClassRecord classRecord : program.classes()) {
            for (FunctionRecord function : classRecord.functions()) {
                if (isVirtual(function)) {
                    slots.computeIfAbsent(signature(function), ignored -> slots.size());
                }
            }
        }
        for (InterfaceRecord interfaceRecord : program.interfaces()) {
            for (FunctionRecord function : interfaceRecord.functions()) {
                if (isVirtual(function)) {
                    slots.computeIfAbsent(signature(function), ignored -> slots.size());
                }
            }
        }
    }

    public int slot(FunctionRecord function) {
        Integer slot = slots.get(signature(function));
        if (slot == null) {
            throw new InvalidIRException(
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
        return methodLabel(owner.name(), function);
    }

    public static String methodLabel(String ownerName, FunctionRecord function) {
        List<String> parameters = function.parameters().stream()
                .map(parameter -> mangleType(parameter.type().name()))
                .toList();
        return ownerName + "." + function.name() + "__"
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
        List<InterfaceMethod> declarations = new ArrayList<>();
        current = classRecord;
        while (current != null) {
            for (String interfaceName : current.interfaces()) {
                collectInterfaceMethods(interfaceName, signature, new java.util.HashSet<>(), declarations);
            }
            current = current.superClass() == null ? null : classes.get(current.superClass());
        }

        List<InterfaceMethod> mostSpecific = declarations.stream()
                .filter(candidate -> declarations.stream().noneMatch(other ->
                        !candidate.owner().equals(other.owner())
                                && isSubinterface(other.owner(), candidate.owner())))
                .toList();
        if (mostSpecific.size() == 1 && mostSpecific.get(0).function().isDefault()) {
            InterfaceMethod method = mostSpecific.get(0);
            return methodLabel(method.owner(), method.function());
        }
        return "0";
    }

    private void collectInterfaceMethods(
            String interfaceName,
            String wantedSignature,
            java.util.Set<String> visited,
            List<InterfaceMethod> declarations) {
        if (!visited.add(interfaceName)) return;
        InterfaceRecord interfaceRecord = interfaces.get(interfaceName);
        if (interfaceRecord == null) return;
        for (FunctionRecord function : interfaceRecord.functions()) {
            if (isVirtual(function) && signature(function).equals(wantedSignature)) {
                boolean alreadyAdded = declarations.stream().anyMatch(method ->
                        method.owner().equals(interfaceName)
                                && signature(method.function()).equals(wantedSignature));
                if (!alreadyAdded) declarations.add(new InterfaceMethod(interfaceName, function));
            }
        }
        for (String parent : interfaceRecord.superInterfaces()) {
            collectInterfaceMethods(parent, wantedSignature, visited, declarations);
        }
    }

    private boolean isSubinterface(String child, String parent) {
        if (child.equals(parent)) return true;
        InterfaceRecord interfaceRecord = interfaces.get(child);
        if (interfaceRecord == null) return false;
        for (String superInterface : interfaceRecord.superInterfaces()) {
            if (isSubinterface(superInterface, parent)) return true;
        }
        return false;
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
