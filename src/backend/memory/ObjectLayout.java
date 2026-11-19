package src.backend.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import src.ast.records.AttributeRecord;
import src.ast.records.ClassRecord;
import src.ast.records.ProgramRecord;

public class ObjectLayout {

    private static class TypeInfo {
        int size;
        int alignment;

        public TypeInfo(int size, int alignment) {
            this.size = size;
            this.alignment = alignment;
        }
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

        for (ClassRecord classNode : program.classes()) {
            classesByName.put(classNode.name(), classNode);
        }

        for (ClassRecord classNode : program.classes()) {
            calculate(classNode, classesByName, layouts);
        }

        return Collections.unmodifiableMap(layouts);
    }

    public ClassLayout calculate(ClassRecord classNode) {
        if (classNode.superClass() != null && !classNode.superClass().equals("Object")) {
            throw new IllegalArgumentException(
                    "Para calcular uma classe com herança, use calculate(ProgramRecord).");
        }
        Map<String, ClassRecord> classesByName = Map.of(classNode.name(), classNode);
        return calculate(classNode, classesByName, new LinkedHashMap<>());
    }

    private ClassLayout calculate(
            ClassRecord classNode,
            Map<String, ClassRecord> classesByName,
            Map<String, ClassLayout> layouts) {
        String className = classNode.name();
        ClassLayout cached = layouts.get(className);
        if (cached != null) {
            return cached;
        }

        List<FieldLayout> fields = new ArrayList<>();
        int currentOffset = 0;
        int classAlignment = 1;

        String parentName = classNode.superClass();
        if (parentName != null && !parentName.equals("Object")) {
            ClassRecord parent = classesByName.get(parentName);
            if (parent == null) {
                throw new IllegalArgumentException(
                        "Classe pai '" + parentName + "' não encontrada ao calcular o layout de '"
                                + className + "'.");
            }

            ClassLayout parentLayout = calculate(parent, classesByName, layouts);
            fields.addAll(parentLayout.fields());
            currentOffset = parentLayout.size();
            classAlignment = parentLayout.alignment();
        }

        for (AttributeRecord attribute : classNode.attributes()) {
            if (attribute.isStatic()) {
                continue;
            }

            String name = attribute.name();
            String type = attribute.type().name();

            TypeInfo info = types.get(type);

            if (info == null && classesByName.containsKey(type)) {
                info = new TypeInfo(8, 8);
            }

            if (info == null && (type.endsWith("[]") || type.equals("String"))) {
                info = new TypeInfo(8, 8);
            }

            if (info == null) {
                throw new IllegalArgumentException(
                        "Tipo não suportado na classe "
                                + className + ": " + type);
            }

            currentOffset = alignUp(
                    currentOffset,
                    info.alignment);

            FieldLayout field = new FieldLayout(
                    name,
                    type,
                    currentOffset,
                    info.size,
                    info.alignment);

            fields.add(field);

            currentOffset += info.size;

            classAlignment = Math.max(
                    classAlignment,
                    info.alignment);
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
