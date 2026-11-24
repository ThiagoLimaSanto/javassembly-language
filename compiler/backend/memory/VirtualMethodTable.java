package compiler.backend.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import compiler.ast.records.ClassRecord;
import compiler.ast.records.FunctionRecord;
import compiler.ast.records.InterfaceRecord;
import compiler.ast.records.ProgramRecord;
import compiler.ast.records.TypeNames;
import compiler.exceptions.InvalidIRException;
import compiler.lexer.TokenType;

public final class VirtualMethodTable {
    private record InterfaceMethod(String owner, FunctionRecord function) {
    }

    private record ClassView(ClassRecord owner, Map<String, String> substitutions) {
    }

    private record InterfaceView(InterfaceRecord owner, Map<String, String> substitutions) {
    }

    private final Map<String, ClassRecord> classes = new LinkedHashMap<>();
    private final Map<String, InterfaceRecord> interfaces = new LinkedHashMap<>();
    private final Map<String, Integer> slots = new LinkedHashMap<>();
    private final Map<String, String> bridgeTargets = new LinkedHashMap<>();

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
        bridgeTargets.clear();
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

    public Map<String, String> bridgeTargets() {
        return Map.copyOf(bridgeTargets);
    }

    public static boolean isVirtual(FunctionRecord function) {
        return !function.isStatic() && function.visibility() != TokenType.PRIVATE;
    }

    public String signature(FunctionRecord function) {
        Map<String, String> methodVariables = erasedTypeVariables(function);
        return function.name() + "(" + String.join(",", function.parameters().stream()
                .map(parameter -> TypeNames.erase(parameter.type().name(), methodVariables))
                .toList()) + ")";
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
        String genericImplementation = resolveGenericImplementation(classRecord, signature);
        if (genericImplementation != null) return genericImplementation;
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

    private String resolveGenericImplementation(ClassRecord root, String wantedSignature) {
        List<ClassView> classViews = new ArrayList<>();
        List<InterfaceView> interfaceViews = new ArrayList<>();
        String rootType = root.name();
        if (!root.typeParameters().isEmpty()) {
            rootType += "<" + String.join(",", root.typeParameters()) + ">";
        }
        collectClassViews(rootType, classViews, interfaceViews, new java.util.HashSet<>());

        for (InterfaceView interfaceView : interfaceViews) {
            for (FunctionRecord requirement : interfaceView.owner().functions()) {
                if (!isVirtual(requirement) || !signature(requirement).equals(wantedSignature)) continue;
                Map<String, String> requiredSubstitutions = new java.util.HashMap<>(
                        interfaceView.substitutions());
                requiredSubstitutions.putAll(methodTypeVariables(requirement));
                List<String> requiredParameters = requirement.parameters().stream()
                        .map(parameter -> TypeNames.substitute(
                                parameter.type().name(), requiredSubstitutions))
                        .toList();

                for (ClassView classView : classViews) {
                    for (FunctionRecord implementation : classView.owner().functions()) {
                        if (!isVirtual(implementation) || implementation.isAbstract()
                                || !implementation.name().equals(requirement.name())
                                || implementation.parameters().size() != requiredParameters.size()) {
                            continue;
                        }
                        Map<String, String> implementationSubstitutions = new java.util.HashMap<>(
                                classView.substitutions());
                        implementationSubstitutions.putAll(methodTypeVariables(implementation));
                        boolean matches = true;
                        for (int index = 0; index < requiredParameters.size(); index++) {
                            String actual = TypeNames.substitute(
                                    implementation.parameters().get(index).type().name(),
                                    implementationSubstitutions);
                            if (!actual.equals(requiredParameters.get(index))) {
                                matches = false;
                                break;
                            }
                        }
                        if (matches) {
                            String target = methodLabel(classView.owner(), implementation);
                            String bridge = bridgeLabel(root, wantedSignature);
                            bridgeTargets.putIfAbsent(bridge, target);
                            return bridge;
                        }
                    }
                }
            }
        }
        return null;
    }

    private String bridgeLabel(ClassRecord owner, String signature) {
        return "__jvs_bridge_" + mangleType(owner.name()) + "_" + mangleType(signature);
    }

    private void collectClassViews(
            String classType,
            List<ClassView> classViews,
            List<InterfaceView> interfaceViews,
            java.util.Set<String> visited) {
        if (!visited.add(classType)) return;
        String className = TypeNames.rawName(classType);
        ClassRecord classRecord = classes.get(className);
        if (classRecord == null) return;
        Map<String, String> substitutions = substitutions(
                classRecord.typeParameters(), TypeNames.arguments(classType));
        classViews.add(new ClassView(classRecord, substitutions));

        for (String interfaceType : classRecord.genericInterfaceTypes()) {
            collectInterfaceViews(TypeNames.substitute(interfaceType, substitutions),
                    interfaceViews, new java.util.HashSet<>());
        }
        if (classRecord.genericSuperType() != null) {
            collectClassViews(TypeNames.substitute(classRecord.genericSuperType(), substitutions),
                    classViews, interfaceViews, visited);
        }
    }

    private void collectInterfaceViews(
            String interfaceType,
            List<InterfaceView> interfaceViews,
            java.util.Set<String> visited) {
        if (!visited.add(interfaceType)) return;
        String interfaceName = TypeNames.rawName(interfaceType);
        InterfaceRecord interfaceRecord = interfaces.get(interfaceName);
        if (interfaceRecord == null) return;
        Map<String, String> substitutions = substitutions(
                interfaceRecord.typeParameters(), TypeNames.arguments(interfaceType));
        interfaceViews.add(new InterfaceView(interfaceRecord, substitutions));
        for (String parentType : interfaceRecord.genericSuperInterfaceTypes()) {
            collectInterfaceViews(TypeNames.substitute(parentType, substitutions),
                    interfaceViews, visited);
        }
    }

    private Map<String, String> substitutions(List<String> parameters, List<String> arguments) {
        Map<String, String> result = new java.util.HashMap<>();
        for (int index = 0; index < Math.min(parameters.size(), arguments.size()); index++) {
            result.put(parameters.get(index), arguments.get(index));
        }
        return Map.copyOf(result);
    }

    private static Map<String, String> methodTypeVariables(FunctionRecord function) {
        Map<String, String> result = new java.util.HashMap<>();
        for (int index = 0; index < function.typeParameters().size(); index++) {
            result.put(function.typeParameters().get(index), "jvs.lang.Object");
        }
        return Map.copyOf(result);
    }

    private Map<String, String> erasedTypeVariables(FunctionRecord function) {
        Map<String, String> result = new java.util.HashMap<>();
        for (ClassRecord owner : classes.values()) {
            if (owner.functions().stream().anyMatch(candidate -> candidate == function)) {
                owner.typeParameters().forEach(parameter -> result.put(
                        parameter, "jvs.lang.Object"));
                break;
            }
        }
        if (result.isEmpty()) {
            for (InterfaceRecord owner : interfaces.values()) {
                if (owner.functions().stream().anyMatch(candidate -> candidate == function)) {
                    owner.typeParameters().forEach(parameter -> result.put(
                            parameter, "jvs.lang.Object"));
                    break;
                }
            }
        }
        result.putAll(methodTypeVariables(function));
        return Map.copyOf(result);
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
