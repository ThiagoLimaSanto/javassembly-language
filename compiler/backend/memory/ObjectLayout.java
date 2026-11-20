package compiler.backend.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import compiler.ast.records.AttributeRecord;
import compiler.ast.records.ClassRecord;
import compiler.ast.records.ProgramRecord;
import compiler.exceptions.ObjectLayoutException;
import compiler.semantic.BuiltinExceptionTypes;

public class ObjectLayout {

    private record TypeInfo(int size, int alignment) {
    }

    private final Map<String, TypeInfo> types = new LinkedHashMap<>();

    public ObjectLayout() {
        types.put("boolean", new TypeInfo(1, 1));
        types.put("char", new TypeInfo(1, 1));
        types.put("int", new TypeInfo(4, 4));
        types.put("float", new TypeInfo(4, 4));
        types.put("long", new TypeInfo(8, 8));
        types.put("double", new TypeInfo(8, 8));
    }

    public Map<String, ClassLayout> calculate(ProgramRecord program) {
        Map<String, ClassRecord> classesByName = new LinkedHashMap<>();
        Map<String, ClassLayout> layouts = new LinkedHashMap<>();
        Set<String> interfaceNames = program.interfaces().stream()
                .map(interfaceNode -> interfaceNode.name())
                .collect(Collectors.toSet());

        for (ClassRecord classNode : program.classes()) {
            classesByName.put(classNode.name(), classNode);
        }

        for (ClassRecord classNode : program.classes()) {
            calculate(classNode, classesByName, interfaceNames, layouts);
        }

        return Collections.unmodifiableMap(layouts);
    }

    public ClassLayout calculate(ClassRecord classNode) {
        if (classNode.superClass() != null
                && !classNode.superClass().equals("jvs.lang.Object")
                && !BuiltinExceptionTypes.isBuiltin(classNode.superClass())) {
            throw new ObjectLayoutException(
                    "Para calcular uma classe com herança, use calculate(ProgramRecord).");
        }
        Map<String, ClassRecord> classesByName = Map.of(classNode.name(), classNode);
        return calculate(classNode, classesByName, Set.of(), new LinkedHashMap<>());
    }

    private ClassLayout calculate(
            ClassRecord classNode,
            Map<String, ClassRecord> classesByName,
            Set<String> interfaceNames,
            Map<String, ClassLayout> layouts) {
        String className = classNode.name();
        ClassLayout cached = layouts.get(className);
        if (cached != null) {
            return cached;
        }

        List<FieldLayout> fields = new ArrayList<>();
        int currentOffset = 8;
        int classAlignment = 8;

        String parentName = classNode.superClass();
        if (parentName != null) {
            ClassRecord parent = classesByName.get(parentName);
            if (parent == null) {
                if (BuiltinExceptionTypes.isBuiltin(parentName)
                        || parentName.equals("jvs.lang.Object")) {
                    parentName = null;
                } else {
                    throw new ObjectLayoutException(
                            "Classe pai '" + parentName + "' não encontrada ao calcular o layout de '"
                                    + className + "'.");
                }
            }
            if (parent != null) {
                ClassLayout parentLayout = calculate(parent, classesByName, interfaceNames, layouts);
                fields.addAll(parentLayout.fields());
                currentOffset = parentLayout.size();
                classAlignment = parentLayout.alignment();
            }
        }

        for (AttributeRecord attribute : classNode.attributes()) {
            if (attribute.isStatic()) {
                continue;
            }

            String name = attribute.name();
            String type = attribute.type().name();

            TypeInfo info = types.get(type);

            if (info == null && (classesByName.containsKey(type) || interfaceNames.contains(type))) {
                info = new TypeInfo(8, 8);
            }

            if (info == null && (type.endsWith("[]") || type.equals("String"))) {
                info = new TypeInfo(8, 8);
            }

            if (info == null) {
                throw new ObjectLayoutException(
                        "Tipo não suportado na classe "
                                + className + ": " + type);
            }

            currentOffset = alignUp(
                    currentOffset,
                    info.alignment());

            FieldLayout field = new FieldLayout(
                    name,
                    type,
                    currentOffset,
                    info.size(),
                    info.alignment());

            fields.add(field);

            currentOffset += info.size();

            classAlignment = Math.max(
                    classAlignment,
                    info.alignment());
        }

        int totalSize = alignUp(
                currentOffset,
                classAlignment);

        ClassLayout layout = new ClassLayout(
                className,
                fields,
                totalSize,
                classAlignment);
        layouts.put(className, layout);
        return layout;
    }

    private int alignUp(int value, int alignment) {
        return (value + alignment - 1) & -alignment;
    }
}
