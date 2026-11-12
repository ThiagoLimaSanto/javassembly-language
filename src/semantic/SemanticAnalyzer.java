package src.semantic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import src.ast.ExpressionInterface;
import src.ast.StatementInterface;
import src.ast.records.*;
import src.ast.visitor.ASTVisitor;
import src.exceptions.SemanticException;
import src.lexer.TokenType;

public class SemanticAnalyzer implements ASTVisitor {

    private VariableTable currentScope = new VariableTable(null);

    private final Map<String, FunctionTable> functionTables = new HashMap<>();
    private final Map<String, ClassRecord> classes = new HashMap<>();
    private final Map<String, InterfaceRecord> interfaces = new HashMap<>();
    private final Map<String, SemanticType> registers = new HashMap<>();

    private String currentClass;
    private FunctionRecord currentFunction;
    private AttributeRecord currentAttribute;
    private VariableTable classScope;
    private boolean currentConstructor;
    private boolean currentConstructorDelegatesToThis;
    private int loopDepth;
    private Set<String> assignedFinalFields = new HashSet<>();
    private Set<VariableSymbolRecord> definitelyAssignedVariables = new HashSet<>();
    private Map<String, String> constructorDelegations = new HashMap<>();

    private final TypeChecker typeChecker = new TypeChecker();


    private void enterScope() {
        currentScope = new VariableTable(currentScope);
    }

    private void exitScope() {
        currentScope = currentScope.getParent();
    }


    private SemanticType convertType(TypeRecord type) {
        return convertType(type.name());
    }

    private SemanticType convertType(String type) {
        return switch (type) {

            case "int" ->
                SemanticType.primitive(TokenType.INT);

            case "double" ->
                SemanticType.primitive(TokenType.DOUBLE);

            case "String" ->
                SemanticType.primitive(TokenType.STRING);

            case "boolean" ->
                SemanticType.primitive(TokenType.BOOLEAN);

            case "void" ->
                SemanticType.primitive(TokenType.VOID);

            default ->
                SemanticType.classType(type);
        };
    }

    private SemanticType convertType(TokenType type) {
        if (type == TokenType.NULL) {
            return SemanticType.nullType();
        }
        return SemanticType.primitive(type);
    }


    private ClassRecord getCurrentClass() {

        if (currentClass == null) {
            return null;
        }

        return classes.get(currentClass);
    }


    private AttributeRecord findAttribute(
            ClassRecord classRecord,
            String name) {

        if (classRecord == null) {
            return null;
        }

        for (AttributeRecord attribute : classRecord.attributes()) {

            if (attribute.name().equals(name)) {
                return attribute;
            }
        }

        return null;
    }

    private AttributeRecord findInheritedAttribute(
            String className,
            String name) {
        Set<String> visited = new HashSet<>();

        while (className != null && visited.add(className)) {
            ClassRecord classNode = classes.get(className);

            if (classNode == null) {
                return null;
            }

            AttributeRecord attribute = findAttribute(classNode, name);

            if (attribute != null) {
                return attribute;
            }

            className = classNode.superClass();
        }

        return null;
    }

    private boolean isStaticContext() {
        return (currentFunction != null && currentFunction.isStatic())
                || (currentFunction == null
                        && currentAttribute != null
                        && currentAttribute.isStatic());
    }

    private int visibilityLevel(TokenType visibility) {
        if (visibility == TokenType.PUBLIC) {
            return 3;
        }
        if (visibility == TokenType.PROTECTED) {
            return 2;
        }
        if (visibility == null) {
            return 1;
        }
        return 0;
    }

    private String packageName(String qualifiedName) {
        int separator = qualifiedName.lastIndexOf('.');
        return separator < 0 ? "" : qualifiedName.substring(0, separator);
    }

    private boolean samePackage(String firstType, String secondType) {
        return java.util.Objects.equals(
                packageName(firstType),
                packageName(secondType));
    }

    private boolean isSubclassOf(String className, String expectedSuperClass) {
        Set<String> visited = new HashSet<>();
        String current = className;

        while (current != null && visited.add(current)) {
            if (current.equals(expectedSuperClass)) {
                return true;
            }

            ClassRecord classNode = classes.get(current);
            current = classNode == null ? null : classNode.superClass();
        }

        return false;
    }

    private boolean isTypeAccessible(
            TokenType visibility,
            String declaringType,
            String accessingType) {
        return visibility == TokenType.PUBLIC
                || samePackage(declaringType, accessingType);
    }

    private boolean isMemberAccessibleFromCurrentClass(
            TokenType visibility,
            String declaringClass) {
        if (visibility == TokenType.PUBLIC) {
            return true;
        }
        if (currentClass == null || declaringClass == null) {
            return false;
        }
        if (visibility == TokenType.PRIVATE) {
            return currentClass.equals(declaringClass);
        }
        if (samePackage(currentClass, declaringClass)) {
            return true;
        }
        return visibility == TokenType.PROTECTED
                && isSubclassOf(currentClass, declaringClass);
    }

    private void assignFinalField(
            AttributeRecord attribute,
            int line,
            int column) {
        if (!attribute.isFinal()) {
            return;
        }

        ClassRecord declaringClass = findDeclaringClass(currentClass, attribute.name());
        if (!currentConstructor
                || currentConstructorDelegatesToThis
                || attribute.value() != null
                || declaringClass == null
                || !declaringClass.name().equals(currentClass)) {
            throw new SemanticException(
                    "Não é permitido modificar o atributo final '"
                            + attribute.name() + "'.",
                    line,
                    column);
        }

        if (loopDepth > 0) {
            throw new SemanticException(
                    "Um atributo final não pode ser inicializado dentro de um laço.",
                    line,
                    column);
        }

        if (!assignedFinalFields.add(attribute.name())) {
            throw new SemanticException(
                    "O atributo final '" + attribute.name()
                            + "' só pode ser inicializado uma vez.",
                    line,
                    column);
        }
    }

    private ConstructorRecord resolveConstructor(NewObjectRecord creation) {
        SemanticType type = convertType(creation.type());
        if (!type.isClass()) {
            throw new SemanticException(
                    "Só é possível instanciar uma classe concreta.",
                    creation.line(),
                    creation.column());
        }

        ClassRecord classNode = classes.get(type.className());
        if (classNode == null) {
            if (interfaces.containsKey(type.className())) {
                throw new SemanticException(
                        "Não é possível instanciar uma interface.",
                        creation.line(),
                        creation.column());
            }
            throw new SemanticException(
                    "Classe '" + type.className() + "' não declarada.",
                    creation.line(),
                    creation.column());
        }

        validateTypeExists(creation.type(), false, creation.line(), creation.column());
        if (classNode.isAbstract()) {
            throw new SemanticException(
                    "Não é possível instanciar a classe abstract '"
                            + classNode.name() + "'.",
                    creation.line(),
                    creation.column());
        }

        List<SemanticType> argumentTypes = new ArrayList<>();
        for (ExpressionInterface argument : creation.arguments()) {
            argumentTypes.add(getExpressionType(argument));
        }

        if (classNode.constructors().isEmpty()) {
            if (!argumentTypes.isEmpty()) {
                throw new SemanticException(
                        noSuitableConstructorMessage(classNode, argumentTypes),
                        creation.line(),
                        creation.column());
            }
            return null;
        }

        List<ConstructorRecord> candidates = new ArrayList<>();
        for (ConstructorRecord constructor : classNode.constructors()) {
            if (constructor.parameters().size() != argumentTypes.size()) {
                continue;
            }
            boolean matchingTypes = true;
            for (int index = 0; index < argumentTypes.size(); index++) {
                SemanticType expected = convertType(constructor.parameters().get(index).type());
                if (!isAssignable(expected, argumentTypes.get(index))) {
                    matchingTypes = false;
                    break;
                }
            }
            if (matchingTypes) {
                candidates.add(constructor);
            }
        }

        if (!candidates.isEmpty()) {
            ConstructorRecord bestMatch = mostSpecificConstructor(candidates);
            if (bestMatch == null) {
                throw new SemanticException(
                        "Chamada ambígua entre construtores de '"
                                + classNode.name() + "'.",
                        creation.line(),
                        creation.column());
            }
            validateMemberVisibility(
                    bestMatch.visibility(),
                    classNode.name(),
                    classNode.name(),
                    classNode.name(),
                    creation.line(),
                    creation.column());
            return bestMatch;
        }

        throw new SemanticException(
                noSuitableConstructorMessage(classNode, argumentTypes),
                creation.line(),
                creation.column());
    }

    private String noSuitableConstructorMessage(
            ClassRecord classNode,
            List<SemanticType> argumentTypes) {
        StringBuilder message = new StringBuilder("Nenhum construtor adequado encontrado para '")
                .append(classNode.name())
                .append(formatTypes(argumentTypes))
                .append("'. Construtores disponíveis: ");

        if (classNode.constructors().isEmpty()) {
            return message.append(classNode.name()).append("().").toString();
        }

        for (int index = 0; index < classNode.constructors().size(); index++) {
            if (index > 0) {
                message.append(", ");
            }
            ConstructorRecord constructor = classNode.constructors().get(index);
            List<String> parameterTypes = new ArrayList<>();
            for (ParameterRecord parameter : constructor.parameters()) {
                parameterTypes.add(parameter.type().name());
            }
            message.append(classNode.name())
                    .append('(')
                    .append(String.join(", ", parameterTypes))
                    .append(')');
        }
        return message.append('.').toString();
    }

    private String formatTypes(List<SemanticType> types) {
        List<String> names = new ArrayList<>();
        for (SemanticType type : types) {
            if (type == null) {
                names.add("desconhecido");
            } else if (type.isClass()) {
                names.add(type.className());
            } else {
                names.add(switch (type.primitiveType()) {
                    case INT -> "int";
                    case DOUBLE -> "double";
                    case STRING -> "String";
                    case BOOLEAN -> "boolean";
                    case VOID -> "void";
                    default -> type.primitiveType().name().toLowerCase();
                });
            }
        }
        return "(" + String.join(", ", names) + ")";
    }

    private String constructorSignature(ConstructorRecord constructor) {
        StringBuilder signature = new StringBuilder("(");
        for (ParameterRecord parameter : constructor.parameters()) {
            signature.append(parameter.type().name()).append(';');
        }
        return signature.append(')').toString();
    }

    private FunctionRecord findConcreteImplementation(
            String className,
            FunctionRecord abstractFunction) {
        Set<String> visited = new HashSet<>();
        while (className != null && visited.add(className)) {
            ClassRecord classNode = classes.get(className);
            if (classNode == null) {
                return null;
            }
            for (FunctionRecord method : classNode.functions()) {
                if (sameSignature(method, abstractFunction)) {
                    return method.isAbstract() ? null : method;
                }
            }
            className = classNode.superClass();
        }
        return null;
    }

    private void validateAbstractMethods(ClassRecord classNode) {
        if (classNode.isAbstract()) {
            return;
        }

        Set<String> visited = new HashSet<>();
        String current = classNode.name();
        while (current != null && visited.add(current)) {
            ClassRecord currentNode = classes.get(current);
            if (currentNode == null) {
                break;
            }
            for (FunctionRecord function : currentNode.functions()) {
                if (function.isAbstract()
                        && findConcreteImplementation(classNode.name(), function) == null) {
                    throw new SemanticException(
                            "A classe concreta '" + classNode.name()
                                    + "' precisa implementar o método abstract '"
                                    + function.name() + "'.",
                            classNode.line(),
                            classNode.column());
                }
            }
            current = currentNode.superClass();
        }
    }

    private boolean canCompleteNormally(BlockRecord block) {
        for (StatementInterface statement : block.commands()) {
            if (!canCompleteNormally(statement)) {
                return false;
            }
        }
        return true;
    }

    private boolean canCompleteNormally(StatementInterface statement) {
        if (statement instanceof ReturnRecord) {
            return false;
        }
        if (statement instanceof BlockRecord block) {
            return canCompleteNormally(block);
        }
        if (statement instanceof WhileRecord loop) {
            return !isAlwaysTrue(loop.expression());
        }
        if (statement instanceof ForRecord loop) {
            return loop.condition() != null && !isAlwaysTrue(loop.condition());
        }
        return true;
    }

    private boolean isAlwaysTrue(ExpressionInterface expression) {
        return expression instanceof LiteralRecord literal
                && Boolean.TRUE.equals(literal.value());
    }

    private void validateFieldAccess(
            AttributeRecord attribute,
            int line,
            int column) {
        if (isStaticContext() && !attribute.isStatic()) {
            throw new SemanticException(
                    "Um membro de instância não pode ser acessado de um contexto estático.",
                    line,
                    column);
        }
    }

    private VariableSymbolRecord resolveVariable(
            String name,
            int line,
            int column) {
        VariableSymbolRecord local = findLocalVariable(name);
        if (local != null) {
            if (!definitelyAssignedVariables.contains(local)) {
                throw new SemanticException(
                        "A variável '" + name
                                + "' pode não ter sido inicializada.",
                        line,
                        column);
            }
            return local;
        }

        AttributeRecord attribute = findInheritedAttribute(currentClass, name);

        if (attribute != null) {
            ClassRecord declaringClass = findDeclaringClass(currentClass, name);

            validateMemberVisibility(
                    attribute.visibility(),
                    declaringClass == null ? null : declaringClass.name(),
                    name,
                    line,
                    column);

            validateFieldAccess(attribute, line, column);

            return new VariableSymbolRecord(
                    attribute.name(),
                    convertType(attribute.type()),
                    attribute.line(),
                    attribute.column());
        }

        throw new SemanticException(
                "Variável ou atributo '" + name + "' não definido.",
                line,
                column);
    }

    private VariableSymbolRecord findLocalVariable(String name) {
        VariableTable scope = currentScope;
        while (scope != null && scope != classScope) {
            VariableSymbolRecord local = scope.resolveCurrent(name);
            if (local != null) {
                return local;
            }
            scope = scope.getParent();
        }
        return null;
    }

    private boolean resolvesToLocalVariable(String name) {
        return findLocalVariable(name) != null;
    }

    private ClassRecord findDeclaringClass(String className, String name) {
        Set<String> visited = new HashSet<>();

        while (className != null && visited.add(className)) {
            ClassRecord classNode = classes.get(className);

            if (classNode == null) {
                return null;
            }

            if (findAttribute(classNode, name) != null) {
                return classNode;
            }

            className = classNode.superClass();
        }

        return null;
    }

    private AttributeRecord resolveObjectAttribute(MemberAccessRecord access) {
        SemanticType receiverType = getExpressionType(access.receiver());

        if (receiverType == null || !receiverType.isClass()) {
            throw new SemanticException(
                    "Acesso a atributo exige um objeto de classe.",
                    access.line(),
                    access.column());
        }

        AttributeRecord attribute = findInheritedAttribute(
                receiverType.className(),
                access.name());

        if (attribute == null) {
            throw new SemanticException(
                    "A classe '" + receiverType.className()
                            + "' não possui o atributo '" + access.name() + "'.",
                    access.line(),
                    access.column());
        }

        ClassRecord declaringClass = findDeclaringClass(
                receiverType.className(),
                access.name());
        validateMemberVisibility(
                attribute.visibility(),
                declaringClass == null ? null : declaringClass.name(),
                access.name(),
                receiverType.className(),
                access.line(),
                access.column());

        return attribute;
    }

    private FunctionRecord resolveObjectMethod(MemberCallRecord call) {
        SemanticType receiverType = getExpressionType(call.receiver());

        if (receiverType == null || !receiverType.isClass()) {
            throw new SemanticException(
                    "Chamada de método exige um objeto de classe.",
                    call.line(),
                    call.column());
        }

        List<SemanticType> argumentTypes = new ArrayList<>();

        for (ExpressionInterface argument : call.arguments()) {
            SemanticType argumentType = getExpressionType(argument);

            if (argumentType == null) {
                throw new SemanticException(
                        "Não foi possível determinar o tipo de um argumento.",
                        call.line(),
                        call.column());
            }

            argumentTypes.add(argumentType);
        }

        InterfaceRecord interfaceNode = interfaces.get(receiverType.className());

        if (interfaceNode != null) {
            List<FunctionRecord> candidates = new ArrayList<>();
            for (FunctionRecord function : interfaceNode.functions()) {
                if (matchesCall(function, call.name(), argumentTypes)) {
                    candidates.add(function);
                }
            }
            FunctionRecord bestMatch = mostSpecificMethod(candidates);
            if (bestMatch != null) {
                return bestMatch;
            }
            if (candidates.size() > 1) {
                throw new SemanticException(
                        "Chamada ambígua do método '" + call.name() + "'.",
                        call.line(),
                        call.column());
            }

            throw new SemanticException(
                    "Método '" + call.name() + "' não encontrado na interface '"
                            + receiverType.className() + "'.",
                    call.line(),
                    call.column());
        }

        String className = receiverType.className();
        boolean methodNameFound = false;
        boolean matchingArityFound = false;
        List<FunctionRecord> candidates = new ArrayList<>();
        Set<String> shadowedSignatures = new HashSet<>();
        Set<String> visited = new HashSet<>();

        while (className != null && visited.add(className)) {
            ClassRecord classNode = classes.get(className);

            if (classNode == null) {
                break;
            }

            for (FunctionRecord function : classNode.functions()) {
                if (!function.name().equals(call.name())
                        || !shadowedSignatures.add(methodSignature(function))
                        || (function.visibility() == TokenType.PRIVATE
                                && !classNode.name().equals(currentClass))) {
                    continue;
                }

                methodNameFound = true;

                if (function.parameters().size() != argumentTypes.size()) {
                    continue;
                }
                matchingArityFound = true;

                if (matchesCall(function, call.name(), argumentTypes)) {
                    candidates.add(function);
                }
            }

            className = classNode.superClass();
        }

        FunctionRecord bestMatch = mostSpecificMethod(candidates);
        if (bestMatch != null) {
            ClassRecord declaringClass = findDeclaringClassForMethod(
                    receiverType.className(),
                    bestMatch);
            validateMemberVisibility(
                    bestMatch.visibility(),
                    declaringClass == null ? null : declaringClass.name(),
                    call.name(),
                    receiverType.className(),
                    call.line(),
                    call.column());
            return bestMatch;
        }
        if (!candidates.isEmpty()) {
            throw new SemanticException(
                    "Chamada ambígua do método '" + call.name() + "'.",
                    call.line(),
                    call.column());
        }

        if (!methodNameFound) {
            throw new SemanticException(
                    "Método '" + call.name() + "' não encontrado ou inacessível na classe '"
                            + receiverType.className() + "'.",
                    call.line(),
                    call.column());
        }

        if (!matchingArityFound) {
            throw new SemanticException(
                    "Quantidade de argumentos incompatível para o método '"
                            + call.name() + "'.",
                    call.line(),
                    call.column());
        }

        throw new SemanticException(
                "Tipos dos argumentos incompatíveis na chamada do método '"
                        + call.name() + "'.",
                call.line(),
                call.column());
    }

    private ClassRecord findDeclaringClassForMethod(
            String className,
            FunctionRecord method) {
        Set<String> visited = new HashSet<>();
        while (className != null && visited.add(className)) {
            ClassRecord classNode = classes.get(className);
            if (classNode == null) {
                return null;
            }
            for (FunctionRecord candidate : classNode.functions()) {
                if (sameSignature(candidate, method)) {
                    return classNode;
                }
            }
            className = classNode.superClass();
        }
        return null;
    }

    private boolean matchesCall(
            FunctionRecord function,
            String name,
            List<SemanticType> argumentTypes) {
        if (!function.name().equals(name)
                || function.parameters().size() != argumentTypes.size()) {
            return false;
        }

        for (int i = 0; i < argumentTypes.size(); i++) {
            SemanticType expected = convertType(
                    function.parameters().get(i).type());
            if (!isAssignable(expected, argumentTypes.get(i))) {
                return false;
            }
        }

        return true;
    }

    private FunctionRecord mostSpecificMethod(List<FunctionRecord> candidates) {
        FunctionRecord best = null;
        for (FunctionRecord candidate : candidates) {
            boolean moreSpecificThanAll = true;
            for (FunctionRecord other : candidates) {
                if (candidate != other && !isMoreSpecific(candidate, other)) {
                    moreSpecificThanAll = false;
                    break;
                }
            }
            if (moreSpecificThanAll) {
                if (best != null) {
                    return null;
                }
                best = candidate;
            }
        }
        return best;
    }

    private boolean isMoreSpecific(
            FunctionRecord candidate,
            FunctionRecord other) {
        boolean strict = false;
        for (int index = 0; index < candidate.parameters().size(); index++) {
            SemanticType candidateType = convertType(
                    candidate.parameters().get(index).type());
            SemanticType otherType = convertType(
                    other.parameters().get(index).type());
            if (!isAssignable(otherType, candidateType)) {
                return false;
            }
            strict |= !candidateType.equals(otherType);
        }
        return strict;
    }

    private void validateMemberVisibility(
            TokenType visibility,
            String declaringClass,
            String memberName,
            String receiverClass,
            int line,
            int column) {
        if (visibility == TokenType.PRIVATE
                && !java.util.Objects.equals(currentClass, declaringClass)) {
            throw new SemanticException(
                    "O membro privado '" + memberName
                            + "' não é acessível nesta classe.",
                    line,
                    column);
        }

        if (visibility == null
                && currentClass != null
                && declaringClass != null
                && !samePackage(currentClass, declaringClass)) {
            throw new SemanticException(
                    "O membro '" + memberName
                            + "' não é acessível fora do pacote.",
                    line,
                    column);
        }

        if (visibility == TokenType.PROTECTED
                && currentClass != null
                && declaringClass != null
                && !samePackage(currentClass, declaringClass)) {
            boolean subclassAccess = isSubclassOf(currentClass, declaringClass);
            boolean validReceiver = receiverClass == null
                    || isSubclassOf(receiverClass, currentClass);
            if (!subclassAccess || !validReceiver) {
                throw new SemanticException(
                        "O membro protected '" + memberName
                                + "' só pode ser acessado no pacote ou por uma subclasse.",
                        line,
                        column);
            }
        }
    }

    private void validateMemberVisibility(
            TokenType visibility,
            String declaringClass,
            String memberName,
            int line,
            int column) {
        validateMemberVisibility(
                visibility,
                declaringClass,
                memberName,
                null,
                line,
                column);
    }

    private void validateTypeExists(
            TypeRecord type,
            boolean allowVoid,
            int line,
            int column) {
        SemanticType semanticType = convertType(type);

        if (semanticType.isNull()) {
            throw new SemanticException(
                    "'null' é um valor, não um tipo declarado.",
                    line,
                    column);
        }

        if (semanticType.isPrimitive()) {
            if (!allowVoid
                    && semanticType.primitiveType() == TokenType.VOID) {
                throw new SemanticException(
                        "O tipo void não pode ser usado aqui.",
                        line,
                        column);
            }
            return;
        }

        ClassRecord classType = classes.get(semanticType.className());
        InterfaceRecord interfaceType = interfaces.get(semanticType.className());
        if (classType == null && interfaceType == null) {
            throw new SemanticException(
                    "Tipo '" + semanticType.className() + "' não declarado.",
                    line,
                    column);
        }

        TokenType visibility = classType != null
                ? classType.visibility()
                : interfaceType.visibility();
        if (currentClass != null
                && !isTypeAccessible(
                        visibility,
                        semanticType.className(),
                        currentClass)) {
            throw new SemanticException(
                    "O tipo '" + semanticType.className()
                            + "' não é público e pertence a outro pacote.",
                    line,
                    column);
        }
    }


    private void validateThisContext(int line, int column) {

        if (currentClass == null) {
            throw new SemanticException(
                    "'this' só pode ser utilizado dentro de uma classe.",
                    line,
                    column);
        }

        if (isStaticContext()) {
            throw new SemanticException(
                    "'this' não pode ser utilizado em métodos estáticos.",
                    line,
                    column);
        }
    }


    private FunctionRecord resolveFunctionCall(CallRecord call) {

        if (currentClass == null) {
            throw new SemanticException(
                    "Chamada de função fora de uma classe.",
                    call.line(),
                    call.column());
        }

        List<SemanticType> argumentTypes = new ArrayList<>();

        for (ExpressionInterface argument : call.arguments()) {
            SemanticType argumentType = getExpressionType(argument);

            if (argumentType == null) {
                throw new SemanticException(
                        "Não foi possível determinar o tipo de um argumento.",
                        call.line(),
                        call.column());
            }

            argumentTypes.add(argumentType);
        }

        String className = currentClass;
        boolean functionFound = false;
        boolean sameParameterCount = false;
        List<FunctionRecord> candidates = new ArrayList<>();
        Set<String> shadowedSignatures = new HashSet<>();
        Set<String> visited = new HashSet<>();

        while (className != null && visited.add(className)) {

            ClassRecord classNode = classes.get(className);

            if (classNode == null) {
                break;
            }

            for (FunctionRecord function : classNode.functions()) {
                if (!function.name().equals(call.name())
                        || !shadowedSignatures.add(methodSignature(function))) {
                    continue;
                }

                if (!isMemberAccessibleFromCurrentClass(
                        function.visibility(),
                        classNode.name())) {
                    continue;
                }

                functionFound = true;

                if (function.parameters().size() != argumentTypes.size()) {
                    continue;
                }

                sameParameterCount = true;
                if (matchesCall(function, call.name(), argumentTypes)) {
                    candidates.add(function);
                }
            }

            className = classNode.superClass();
        }

        FunctionRecord bestMatch = mostSpecificMethod(candidates);
        if (bestMatch != null) {
            if (isStaticContext() && !bestMatch.isStatic()) {
                throw new SemanticException(
                        "Um método de instância não pode ser chamado de um contexto estático.",
                        call.line(),
                        call.column());
            }
            return bestMatch;
        }
        if (!candidates.isEmpty()) {
            throw new SemanticException(
                    "Chamada ambígua da função '" + call.name() + "'.",
                    call.line(),
                    call.column());
        }

        if (!functionFound) {
            throw new SemanticException(
                    "Função '" + call.name() + "' não declarada.",
                    call.line(),
                    call.column());
        }

        if (!sameParameterCount) {
            throw new SemanticException(
                    "Quantidade de parâmetros incompatível na chamada da função '"
                            + call.name() + "'.",
                    call.line(),
                    call.column());
        }

        throw new SemanticException(
                "Tipos dos argumentos incompatíveis na chamada da função '"
                        + call.name() + "'.",
                call.line(),
                call.column());
    }

    private AttributeRecord resolveSuperAttribute(SuperAttributeRecord access) {
        validateThisContext(access.line(), access.column());

        ClassRecord classNode = getCurrentClass();
        String superClass = classNode == null ? null : classNode.superClass();

        if (superClass == null) {
            throw new SemanticException(
                    "'super' só pode ser utilizado em uma classe com classe pai.",
                    access.line(),
                    access.column());
        }

        Set<String> visited = new HashSet<>();

        while (superClass != null && visited.add(superClass)) {
            ClassRecord parent = classes.get(superClass);

            if (parent == null) {
                break;
            }

            AttributeRecord attribute = findAttribute(parent, access.name());

            if (attribute != null) {
                if (!isMemberAccessibleFromCurrentClass(
                        attribute.visibility(),
                        parent.name())) {
                    throw new SemanticException(
                            "O atributo '" + access.name()
                                    + "' não é acessível na classe pai.",
                            access.line(),
                            access.column());
                }

                return attribute;
            }

            superClass = parent.superClass();
        }

        throw new SemanticException(
                "Atributo '" + access.name()
                + "' não declarado na hierarquia da classe pai.",
                access.line(),
                access.column());
    }

    private FunctionRecord resolveSuperCall(SuperCallRecord call) {
        validateThisContext(call.line(), call.column());

        ClassRecord classNode = getCurrentClass();
        String superClass = classNode == null ? null : classNode.superClass();

        if (superClass == null) {
            throw new SemanticException(
                    "'super' só pode ser utilizado em uma classe com classe pai.",
                    call.line(),
                    call.column());
        }

        List<SemanticType> argumentTypes = new ArrayList<>();

        for (ExpressionInterface argument : call.arguments()) {
            SemanticType argumentType = getExpressionType(argument);

            if (argumentType == null) {
                throw new SemanticException(
                "Não foi possível determinar o tipo de um argumento.",
                call.line(),
                call.column());
            }

            argumentTypes.add(argumentType);
        }

        boolean methodFound = false;
        boolean sameParameterCount = false;
        List<FunctionRecord> candidates = new ArrayList<>();
        Set<String> shadowedSignatures = new HashSet<>();
        Set<String> visited = new HashSet<>();

        while (superClass != null && visited.add(superClass)) {
            ClassRecord parent = classes.get(superClass);

            if (parent == null) {
                break;
            }

            for (FunctionRecord function : parent.functions()) {
                if (!function.name().equals(call.name())
                        || !shadowedSignatures.add(methodSignature(function))
                        || !isMemberAccessibleFromCurrentClass(
                                function.visibility(),
                                parent.name())) {
                    continue;
                }

                methodFound = true;

                if (function.parameters().size() != argumentTypes.size()) {
                    continue;
                }

                sameParameterCount = true;
                if (matchesCall(function, call.name(), argumentTypes)) {
                    candidates.add(function);
                }
            }

            superClass = parent.superClass();
        }

        FunctionRecord bestMatch = mostSpecificMethod(candidates);
        if (bestMatch != null) {
            ClassRecord declaringClass = findDeclaringClassForMethod(
                    getCurrentClass().superClass(),
                    bestMatch);
            validateMemberVisibility(
                    bestMatch.visibility(),
                    declaringClass == null ? null : declaringClass.name(),
                    call.name(),
                    call.line(),
                    call.column());
            return bestMatch;
        }
        if (!candidates.isEmpty()) {
            throw new SemanticException(
                    "Chamada ambígua do método '" + call.name() + "'.",
                    call.line(),
                    call.column());
        }

        if (!methodFound) {
            throw new SemanticException(
                    "Método '" + call.name()
                    + "' não declarado ou inacessível na classe pai.",
                    call.line(),
                    call.column());
        }

        if (!sameParameterCount) {
            throw new SemanticException(
                    "Quantidade de parâmetros incompatível na chamada de '"
                    + call.name() + "'.",
                    call.line(),
                    call.column());
        }

        throw new SemanticException(
                "Tipos dos argumentos incompatíveis na chamada de '"
                + call.name() + "'.",
                call.line(),
                call.column());
    }


    private FunctionRecord findInheritedMethod(
            String superClass,
            FunctionRecord function) {

        Set<String> visited = new HashSet<>();

        while (superClass != null && visited.add(superClass)) {

            ClassRecord parent = classes.get(superClass);

            if (parent == null) {
                return null;
            }

            for (FunctionRecord parentFunction : parent.functions()) {

                if (parentFunction.visibility() == TokenType.PRIVATE
                        || (parentFunction.visibility() == null
                                && !samePackage(currentClass, parent.name()))) {
                    continue;
                }

                if (sameSignature(parentFunction, function)) {
                    return parentFunction;
                }
            }

            superClass = parent.superClass();
        }

        return null;
    }

    private void validateClassInheritance(ClassRecord classNode) {

        Set<String> visited = new HashSet<>();

        ClassRecord current = classNode;

        while (current != null) {

            if (!visited.add(current.name())) {
                throw new SemanticException(
                        "Herança circular detectada envolvendo a classe '"
                                + current.name() + "'.",
                        current.line(),
                        current.column());
            }

            if (current.superClass() == null) {
                break;
            }

            ClassRecord parent = classes.get(current.superClass());

            if (parent == null) {
                if (interfaces.containsKey(current.superClass())) {
                    throw new SemanticException(
                            "Uma interface não pode ser usada como classe pai.",
                            current.line(),
                            current.column());
                }

                throw new SemanticException(
                        "Classe pai '" + current.superClass()
                                + "' não declarada.",
                        current.line(),
                        current.column());
            }

            if (!isTypeAccessible(
                    parent.visibility(),
                    parent.name(),
                    classNode.name())) {
                throw new SemanticException(
                        "A classe pai '" + parent.name()
                                + "' não é acessível neste pacote.",
                        current.line(),
                        current.column());
            }

            current = parent;
        }

        if (classNode.superClass() != null && classNode.constructors().isEmpty()) {
            validateImplicitSuperConstructor(
                    classNode,
                    classes.get(classNode.superClass()));
        }
    }

    private ConstructorRecord resolveConstructorInvocation(
            ClassRecord targetClass,
            List<ExpressionInterface> arguments,
            int line,
            int column) {
        List<SemanticType> argumentTypes = new ArrayList<>();
        for (ExpressionInterface argument : arguments) {
            argumentTypes.add(getExpressionType(argument));
        }

        if (targetClass.constructors().isEmpty()) {
            if (argumentTypes.isEmpty()) {
                return null;
            }
            throw new SemanticException(
                    "O construtor implícito de '" + targetClass.name()
                            + "' não recebe argumentos.",
                    line,
                    column);
        }

        List<ConstructorRecord> candidates = new ArrayList<>();

        for (ConstructorRecord constructor : targetClass.constructors()) {
            if (constructor.parameters().size() != argumentTypes.size()) {
                continue;
            }

            boolean compatible = true;
            for (int index = 0; index < argumentTypes.size(); index++) {
                SemanticType expected = convertType(
                        constructor.parameters().get(index).type());
                SemanticType received = argumentTypes.get(index);
                if (!isAssignable(expected, received)) {
                    compatible = false;
                    break;
                }
            }

            if (compatible) {
                candidates.add(constructor);
            }
        }

        if (!candidates.isEmpty()) {
            ConstructorRecord bestMatch = mostSpecificConstructor(candidates);
            if (bestMatch != null) {
                return bestMatch;
            }
            throw new SemanticException(
                    "Invocação ambígua de construtor para '" + targetClass.name() + "'.",
                    line,
                    column);
        }
        throw new SemanticException(
                noSuitableConstructorMessage(targetClass, argumentTypes),
                line,
                column);
    }

    private ConstructorRecord mostSpecificConstructor(
            List<ConstructorRecord> candidates) {
        ConstructorRecord best = null;
        for (ConstructorRecord candidate : candidates) {
            boolean moreSpecificThanAll = true;
            for (ConstructorRecord other : candidates) {
                if (candidate != other && !isMoreSpecific(candidate, other)) {
                    moreSpecificThanAll = false;
                    break;
                }
            }
            if (moreSpecificThanAll) {
                if (best != null) {
                    return null;
                }
                best = candidate;
            }
        }
        return best;
    }

    private boolean isMoreSpecific(
            ConstructorRecord candidate,
            ConstructorRecord other) {
        boolean strict = false;
        for (int index = 0; index < candidate.parameters().size(); index++) {
            SemanticType candidateType = convertType(
                    candidate.parameters().get(index).type());
            SemanticType otherType = convertType(
                    other.parameters().get(index).type());
            if (!isAssignable(otherType, candidateType)) {
                return false;
            }
            strict |= !candidateType.equals(otherType);
        }
        return strict;
    }

    private boolean isConstructorAccessible(
            ConstructorRecord constructor,
            ClassRecord declaringClass,
            boolean superInvocation) {
        if (constructor.visibility() == TokenType.PUBLIC) {
            return true;
        }
        if (constructor.visibility() == TokenType.PRIVATE) {
            return !superInvocation && currentClass.equals(declaringClass.name());
        }
        if (samePackage(currentClass, declaringClass.name())) {
            return true;
        }
        return constructor.visibility() == TokenType.PROTECTED
                && isSubclassOf(currentClass, declaringClass.name());
    }

    private ConstructorRecord validateConstructorInvocation(ConstructorRecord constructor) {
        ConstructorInvocationRecord invocation = constructor.invocation();
        ClassRecord currentClassNode = getCurrentClass();

        if (invocation == null) {
            if (currentClassNode.superClass() != null) {
                ClassRecord parent = classes.get(currentClassNode.superClass());
                ConstructorRecord parentConstructor = resolveConstructorInvocation(
                        parent,
                        List.of(),
                        constructor.line(),
                        constructor.column());
                if (parentConstructor != null
                        && !isConstructorAccessible(parentConstructor, parent, true)) {
                    throw new SemanticException(
                            "O construtor super() de '" + parent.name() + "' não é acessível.",
                            constructor.line(),
                            constructor.column());
                }
            }
            return null;
        }

        ClassRecord targetClass;
        if (invocation.delegatesToThis()) {
            targetClass = currentClassNode;
        } else {
            if (currentClassNode.superClass() == null) {
                throw new SemanticException(
                        "super(...) só pode ser usado em uma classe com classe pai.",
                        invocation.line(),
                        invocation.column());
            }
            targetClass = classes.get(currentClassNode.superClass());
        }

        ConstructorRecord targetConstructor = resolveConstructorInvocation(
                targetClass,
                invocation.arguments(),
                invocation.line(),
                invocation.column());
        if (targetConstructor != null
                && !isConstructorAccessible(
                        targetConstructor,
                        targetClass,
                        !invocation.delegatesToThis())) {
            throw new SemanticException(
                    "O construtor de '" + targetClass.name() + "' não é acessível.",
                    invocation.line(),
                    invocation.column());
        }
        return invocation.delegatesToThis() ? targetConstructor : null;
    }

    private void validateConstructorDelegationCycles(ClassRecord classNode) {
        for (ConstructorRecord constructor : classNode.constructors()) {
            Set<String> visited = new HashSet<>();
            String current = constructorSignature(constructor);
            while (current != null) {
                if (!visited.add(current)) {
                    throw new SemanticException(
                            "Ciclo de chamadas entre construtores da classe '"
                                    + classNode.name() + "'.",
                            constructor.line(),
                            constructor.column());
                }
                current = constructorDelegations.get(current);
            }
        }
    }

    private void validateImplicitSuperConstructor(
            ClassRecord child,
            ClassRecord parent) {
        if (parent.constructors().isEmpty()
                || hasAccessibleNoArgumentConstructor(parent, child)) {
            return;
        }

        throw new SemanticException(
                "A classe '" + child.name()
                        + "' precisa de um super() acessível em '"
                        + parent.name() + "' para o construtor implícito.",
                child.line(),
                child.column());
    }

    private boolean hasAccessibleNoArgumentConstructor(
            ClassRecord parent,
            ClassRecord child) {
        for (ConstructorRecord constructor : parent.constructors()) {
            if (!constructor.parameters().isEmpty()) {
                continue;
            }
            if (constructor.visibility() == TokenType.PUBLIC
                    || (constructor.visibility() == TokenType.PROTECTED
                            && (samePackage(child.name(), parent.name())
                                    || isSubclassOf(child.name(), parent.name())))
                    || (constructor.visibility() == null
                            && samePackage(child.name(), parent.name()))) {
                return true;
            }
        }
        return false;
    }

    private void validateImplementedInterfaces(ClassRecord classNode) {

        Set<String> implemented = new HashSet<>();
        ClassRecord current = classNode;

        while (current != null) {
            Set<String> declaredOnClass = new HashSet<>();

            for (String interfaceName : current.interfaces()) {
                if (!declaredOnClass.add(interfaceName)) {
                    throw new SemanticException(
                            "Interface '" + interfaceName
                                    + "' implementada mais de uma vez.",
                            current.line(),
                            current.column());
                }

                if (!implemented.add(interfaceName)) {
                    continue;
                }

                InterfaceRecord interfaceNode = interfaces.get(interfaceName);

                if (interfaceNode == null) {
                    throw new SemanticException(
                            "'" + interfaceName + "' não é uma interface declarada.",
                            current.line(),
                            current.column());
                }

                if (!isTypeAccessible(
                        interfaceNode.visibility(),
                        interfaceNode.name(),
                        classNode.name())) {
                    throw new SemanticException(
                            "A interface '" + interfaceName
                                    + "' não é acessível neste pacote.",
                            current.line(),
                            current.column());
                }

                for (FunctionRecord requirement : interfaceNode.functions()) {
                    FunctionRecord implementation = findClassMethod(
                            classNode.name(),
                            requirement);

                    if ((implementation == null || implementation.isAbstract())
                            && classNode.isAbstract()) {
                        continue;
                    }

                    if (implementation == null || implementation.isAbstract()) {
                        throw new SemanticException(
                                "A classe '" + classNode.name()
                                        + "' não implementa o método '"
                                        + requirement.name() + "'.",
                                classNode.line(),
                                classNode.column());
                    }

                    if (implementation.visibility() != TokenType.PUBLIC
                            || implementation.isStatic()) {
                        throw new SemanticException(
                                "O método '" + requirement.name()
                                        + "' deve ser público e de instância para implementar a interface.",
                                implementation.line(),
                                implementation.column());
                    }

                    SemanticType expectedReturn = convertType(
                            requirement.returnType());
                    SemanticType actualReturn = convertType(
                            implementation.returnType());

                    if (!isValidReturnSubstitution(expectedReturn, actualReturn)) {
                        throw new SemanticException(
                                "Tipo de retorno incompatível na implementação do método '"
                                        + requirement.name() + "'.",
                                implementation.line(),
                                implementation.column());
                    }
                }
            }

            current = current.superClass() == null
                    ? null
                    : classes.get(current.superClass());
        }
    }

    private FunctionRecord findClassMethod(
            String className,
            FunctionRecord signature) {
        Set<String> visited = new HashSet<>();

        while (className != null && visited.add(className)) {
            ClassRecord classNode = classes.get(className);

            if (classNode == null) {
                return null;
            }

            for (FunctionRecord function : classNode.functions()) {
                if (sameSignature(function, signature)) {
                    return function;
                }
            }

            className = classNode.superClass();
        }

        return null;
    }


    private void validateOverride(
            ClassRecord classNode,
            FunctionRecord function) {

        FunctionRecord inherited = findInheritedMethod(
                classNode.superClass(),
                function);

        if (inherited == null) {
            return;
        }

        if (inherited.isFinal()) {
            throw new SemanticException(
                    "O método '" + function.name()
                            + "' não pode ser sobrescrito porque é final.",
                    function.line(),
                    function.column());
        }

        if (!isValidReturnSubstitution(
                convertType(inherited.returnType()),
                convertType(function.returnType()))) {
            throw new SemanticException(
                    "Tipo de retorno incompatível na sobrescrita do método '"
                            + function.name() + "'.",
                    function.line(),
                    function.column());
        }

        if (visibilityLevel(function.visibility())
                < visibilityLevel(inherited.visibility())) {

            throw new SemanticException(
                    "O método '" + function.name()
                            + "' não pode reduzir sua visibilidade.",
                    function.line(),
                    function.column());
        }

        if (inherited.isStatic() != function.isStatic()) {
            throw new SemanticException(
                    "O método '" + function.name()
                            + "' deve manter o modificador static da classe pai.",
                    function.line(),
                    function.column());
        }
    }

    private boolean sameSignature(
            FunctionRecord first,
            FunctionRecord second) {

        if (!first.name().equals(second.name())) {
            return false;
        }

        if (first.parameters().size() != second.parameters().size()) {
            return false;
        }

        for (int i = 0; i < first.parameters().size(); i++) {

            TypeRecord firstType = first.parameters().get(i).type();
            TypeRecord secondType = second.parameters().get(i).type();

            if (!firstType.name().equals(secondType.name())) {
                return false;
            }
        }

        return true;
    }


    private SemanticType getExpressionType(ExpressionInterface expression) {

        if (expression instanceof LiteralRecord literal) {
            return convertType(literal.getType());
        }

        if (expression instanceof CallRecord call) {
            FunctionRecord function = resolveFunctionCall(call);
            return convertType(function.returnType());
        }

        if (expression instanceof VariableRecord variable) {
            VariableSymbolRecord symbol = resolveVariable(
                    variable.name(),
                    variable.line(),
                    variable.column());
            return symbol.type();
        }

        if (expression instanceof MemberAccessRecord access) {
            return convertType(resolveObjectAttribute(access).type());
        }

        if (expression instanceof MemberCallRecord call) {
            return convertType(resolveObjectMethod(call).returnType());
        }

        if (expression instanceof NewObjectRecord creation) {
            resolveConstructor(creation);
            return convertType(creation.type());
        }

        if (expression instanceof ThisRecord record) {
            validateThisContext(record.line(), record.column());
            return SemanticType.classType(currentClass);
        }

        if (expression instanceof ThisAttributeRecord attributeExpression) {
            validateThisContext(
                    attributeExpression.line(),
                    attributeExpression.column());

            AttributeRecord attribute = findInheritedAttribute(
                    currentClass,
                    attributeExpression.name());

            if (attribute == null) {
                throw new SemanticException(
                        "Atributo '" + attributeExpression.name()
                                + "' não declarado na classe '"
                                + currentClass + "'.",
                        attributeExpression.line(),
                        attributeExpression.column());
            }

            ClassRecord declaringClass = findDeclaringClass(
                    currentClass,
                    attributeExpression.name());

            validateMemberVisibility(
                    attribute.visibility(),
                    declaringClass == null ? null : declaringClass.name(),
                    attribute.name(),
                    attributeExpression.line(),
                    attributeExpression.column());

            return convertType(attribute.type());
        }

        if (expression instanceof ThisCallRecord call) {
            validateThisContext(call.line(), call.column());
            return convertType(resolveFunctionCall(
                    new CallRecord(
                            call.name(),
                            call.arguments(),
                            call.line(),
                            call.column()))
                    .returnType());
        }

        if (expression instanceof SuperAttributeRecord attributeExpression) {
            return convertType(
                    resolveSuperAttribute(attributeExpression).type());
        }

        if (expression instanceof SuperCallRecord call) {
            return convertType(resolveSuperCall(call).returnType());
        }

        if (expression instanceof BinaryExpressionRecord operation) {
            return validateOperation(operation);
        }

        if (expression instanceof RegisterRecord) {
            RegisterRecord register = (RegisterRecord) expression;
            validateRegisterName(register);

            SemanticType type = registers.get(register.name());

            if (type == null) {
                throw new SemanticException(
                        "Registrador '" + register.name()
                                + "' não foi declarado globalmente.",
                        register.line(),
                        register.column());
            }

            return type;
        }

        throw new IllegalStateException(
                "Tipo de expressão não implementado: "
                        + expression.getClass().getSimpleName());
    }


    private SemanticType validateOperation(BinaryExpressionRecord operation) {

        SemanticType left = getExpressionType(operation.left());
        SemanticType right = getExpressionType(operation.right());

        if (left == null || right == null) {
            throw new SemanticException(
                    "Não foi possível determinar o tipo dos operandos.",
                    operation.line(),
                    operation.column());
        }

        TokenType operator = operation.operator();

        switch (operator) {

            case ADD:
            case SUB:
            case MUL:
            case DIV:

                if (!isNumeric(left) || !isNumeric(right)) {
                    throw new SemanticException(
                            "Operação aritmética exige operandos numéricos.",
                            operation.line(),
                            operation.column());
                }

                if (left.primitiveType() == TokenType.DOUBLE
                        || right.primitiveType() == TokenType.DOUBLE) {

                    return SemanticType.primitive(TokenType.DOUBLE);
                }

                return SemanticType.primitive(TokenType.INT);

            case AND:
            case OR:
                if (!isBoolean(left) || !isBoolean(right)) {
                    throw new SemanticException(
                            "O operador '" + operator.name().toLowerCase()
                                    + "' exige operandos booleanos.",
                            operation.line(),
                            operation.column());
                }

                return SemanticType.primitive(TokenType.BOOLEAN);

            case EQ:
            case NE:

                if (!areCompatible(left, right)) {
                    throw new SemanticException(
                            "Comparação exige operandos de tipos compatíveis.",
                            operation.line(),
                            operation.column());
                }

                return SemanticType.primitive(TokenType.BOOLEAN);

            case LT:
            case GT:
            case LE:
            case GE:

                if (!isNumeric(left) || !isNumeric(right)) {
                    throw new SemanticException(
                            "Comparação exige operandos numéricos.",
                            operation.line(),
                            operation.column());
                }

                return SemanticType.primitive(TokenType.BOOLEAN);

            default:

                throw new SemanticException(
                        "Operador não suportado na análise semântica: "
                                + operator,
                        operation.line(),
                        operation.column());
        }
    }

    private boolean isNumeric(SemanticType type) {

        if (type == null || !type.isPrimitive()) {
            return false;
        }

        return type.primitiveType() == TokenType.INT
                || type.primitiveType() == TokenType.DOUBLE;
    }

    private boolean isBoolean(SemanticType type) {
        return type != null
                && type.isPrimitive()
                && type.primitiveType() == TokenType.BOOLEAN;
    }

    private void validateRegisterAssignment(
            SemanticType expected,
            SemanticType received,
            int line,
            int column) {
        validateAssignableType(expected, received, line, column);
    }

    private boolean areCompatible(
            SemanticType first,
            SemanticType second) {

        if (first.equals(second)) {
            return true;
        }

        if (first.isNull()) {
            return isReferenceType(second);
        }
        if (second.isNull()) {
            return isReferenceType(first);
        }

        if (first.isClass() && second.isClass()) {
            return isAssignable(first, second) || isAssignable(second, first);
        }

        return isNumeric(first) && isNumeric(second);
    }

    private boolean isValidReturnSubstitution(
            SemanticType expected,
            SemanticType actual) {
        if (expected.equals(actual)) {
            return true;
        }
        return expected.isClass()
                && actual.isClass()
                && isAssignable(expected, actual);
    }

    private boolean isReferenceType(SemanticType type) {
        return type.isClass()
                || (type.isPrimitive()
                        && type.primitiveType() == TokenType.STRING);
    }

    private boolean isAssignable(
            SemanticType expected,
            SemanticType received) {
        if (received.isNull()) {
            return isReferenceType(expected);
        }

        if (expected.equals(received)) {
            return true;
        }

        if (expected.isPrimitive() && received.isPrimitive()) {
            return expected.primitiveType() == TokenType.DOUBLE
                    && received.primitiveType() == TokenType.INT;
        }

        if (!expected.isClass() || !received.isClass()) {
            return false;
        }

        String className = received.className();
        Set<String> visitedClasses = new HashSet<>();

        while (className != null && visitedClasses.add(className)) {
            if (className.equals(expected.className())) {
                return true;
            }

            ClassRecord classNode = classes.get(className);

            if (classNode == null) {
                return false;
            }

            if (classNode.interfaces().contains(expected.className())) {
                return true;
            }

            className = classNode.superClass();
        }

        return false;
    }

    private void validateAssignableType(
            SemanticType expected,
            SemanticType received,
            int line,
            int column) {
        if (!isAssignable(expected, received)) {
            typeChecker.validateType(expected, received, line, column);
        }
    }


    @Override
    public void visit(ProgramRecord node) {

        for (InterfaceRecord interfaceNode : node.interfaces()) {

            if (interfaces.containsKey(interfaceNode.name())) {
                throw new SemanticException(
                        "Interface '" + interfaceNode.name()
                                + "' já declarada.",
                        interfaceNode.line(),
                        interfaceNode.column());
            }

            interfaces.put(interfaceNode.name(), interfaceNode);
        }

        for (ClassRecord classNode : node.classes()) {

            if (classes.containsKey(classNode.name())) {
                throw new SemanticException(
                        "Classe '" + classNode.name()
                                + "' já declarada.",
                        classNode.line(),
                        classNode.column());
            }

            classes.put(classNode.name(), classNode);
        }

        for (RegisterDeclarationRecord register : node.registers()) {
            register.accept(this);
        }

        for (ClassRecord classNode : node.classes()) {

            if (interfaces.containsKey(classNode.name())) {
                throw new SemanticException(
                        "Já existe uma interface com o nome '"
                                + classNode.name() + "'.",
                        classNode.line(),
                        classNode.column());
            }
        }

        for (ClassRecord classNode : node.classes()) {

            validateClassInheritance(classNode);
            validateImplementedInterfaces(classNode);
            validateAbstractMethods(classNode);
        }

        for (InterfaceRecord interfaceNode : node.interfaces()) {
            interfaceNode.accept(this);
        }

        for (ClassRecord classNode : node.classes()) {
            classNode.accept(this);
        }
    }


    @Override
    public void visit(ClassRecord node) {

        enterScope();

        String previousClass = currentClass;
        VariableTable previousClassScope = classScope;
        currentClass = node.name();
        classScope = currentScope;

        FunctionTable functionTable = new FunctionTable();

        functionTables.put(currentClass, functionTable);
        Map<String, String> previousConstructorDelegations = constructorDelegations;
        constructorDelegations = new HashMap<>();

        try {

            Set<String> constructorSignatures = new HashSet<>();
            for (ConstructorRecord constructor : node.constructors()) {
                if (!constructorSignatures.add(constructorSignature(constructor))) {
                    throw new SemanticException(
                            "Construtor com os mesmos tipos de parâmetros já declarado.",
                            constructor.line(),
                            constructor.column());
                }
            }

            for (FunctionRecord function : node.functions()) {
                if (function.isAbstract() && !node.isAbstract()) {
                    throw new SemanticException(
                            "A classe '" + node.name()
                                    + "' precisa ser abstract para declarar método abstract.",
                            function.line(),
                            function.column());
                }
                if (function.isAbstract()
                        && (function.isStatic() || function.isFinal()
                                || function.visibility() == TokenType.PRIVATE)) {
                    throw new SemanticException(
                            "Método abstract não pode ser private, static ou final.",
                            function.line(),
                            function.column());
                }
                validateOverride(node, function);

                functionTable.define(function);
            }

            for (AttributeRecord attribute : node.attributes()) {
                attribute.accept(this);
            }

            for (FunctionRecord function : node.functions()) {
                function.accept(this);
            }

            for (ConstructorRecord constructor : node.constructors()) {
                constructor.accept(this);
            }
            validateConstructorDelegationCycles(node);

        } finally {

            exitScope();
            currentClass = previousClass;
            classScope = previousClassScope;
            constructorDelegations = previousConstructorDelegations;
        }
    }


    @Override
    public void visit(FunctionRecord node) {

        validateTypeExists(
                node.returnType(),
                true,
                node.line(),
                node.column());

        FunctionRecord previousFunction = currentFunction;
        Set<VariableSymbolRecord> previousAssignedVariables =
                definitelyAssignedVariables;

        currentFunction = node;
        definitelyAssignedVariables = new HashSet<>();

        enterScope();

        try {

            for (ParameterRecord parameter : node.parameters()) {
                parameter.accept(this);
            }

            if (node.block() != null) {
                node.block().accept(this);

                SemanticType returnType = convertType(node.returnType());
                if (!node.isAbstract()
                        && !(returnType.isPrimitive()
                                && returnType.primitiveType() == TokenType.VOID)
                        && canCompleteNormally(node.block())) {
                    throw new SemanticException(
                            "A função '" + node.name()
                                    + "' precisa retornar um valor em todos os caminhos.",
                            node.line(),
                            node.column());
                }
            }

        } finally {

            exitScope();
            currentFunction = previousFunction;
            definitelyAssignedVariables = previousAssignedVariables;
        }
    }

    @Override
    public void visit(ConstructorRecord node) {
        FunctionRecord previousFunction = currentFunction;
        boolean previousConstructor = currentConstructor;
        boolean previousDelegation = currentConstructorDelegatesToThis;
        Set<String> previousAssignedFinalFields = assignedFinalFields;
        Set<VariableSymbolRecord> previousAssignedVariables =
                definitelyAssignedVariables;
        currentConstructor = true;
        currentConstructorDelegatesToThis = node.invocation() != null
                && node.invocation().delegatesToThis();
        assignedFinalFields = new HashSet<>();
        definitelyAssignedVariables = new HashSet<>();
        currentFunction = new FunctionRecord(
                node.visibility(),
                false,
                false,
                new TypeRecord("void"),
                currentClass,
                node.parameters(),
                node.block(),
                node.line(),
                node.column());

        enterScope();
        try {
            for (ParameterRecord parameter : node.parameters()) {
                parameter.accept(this);
            }
            ConstructorRecord delegatedConstructor = validateConstructorInvocation(node);
            if (delegatedConstructor != null) {
                constructorDelegations.put(
                        constructorSignature(node),
                        constructorSignature(delegatedConstructor));
            }
            node.block().accept(this);
            if (node.invocation() == null
                    || !node.invocation().delegatesToThis()) {
                for (AttributeRecord attribute : getCurrentClass().attributes()) {
                    if (attribute.isFinal()
                            && attribute.value() == null
                            && !assignedFinalFields.contains(attribute.name())) {
                        throw new SemanticException(
                                "O atributo final '" + attribute.name()
                                        + "' precisa ser inicializado em todo construtor.",
                                node.line(),
                                node.column());
                    }
                }
            }
        } finally {
            exitScope();
            currentFunction = previousFunction;
            currentConstructor = previousConstructor;
            currentConstructorDelegatesToThis = previousDelegation;
            assignedFinalFields = previousAssignedFinalFields;
            definitelyAssignedVariables = previousAssignedVariables;
        }
    }


    @Override
    public void visit(BlockRecord node) {

        enterScope();

        try {

            for (StatementInterface statement : node.commands()) {
                statement.accept(this);
            }

        } finally {
            exitScope();
        }
    }


    @Override
    public void visit(AttributeRecord node) {

        validateTypeExists(
                node.type(),
                false,
                node.line(),
                node.column());

        if (node.isFinal()
                && node.value() == null
                && getCurrentClass().constructors().isEmpty()) {
            throw new SemanticException(
                    "O atributo final '" + node.name()
                            + "' deve ser inicializado na declaração ou em um construtor explícito.",
                    node.line(),
                    node.column());
        }

        SemanticType declaredType = convertType(node.type());

        VariableSymbolRecord symbol = new VariableSymbolRecord(
                node.name(),
                declaredType,
                node.line(),
                node.column());

        currentScope.define(symbol);

        AttributeRecord previousAttribute = currentAttribute;
        currentAttribute = node;

        try {
            if (node.value() != null) {
                SemanticType valueType = getExpressionType(node.value());
                validateAssignableType(
                        declaredType,
                        valueType,
                        node.line(),
                        node.column());
            }
        } finally {
            currentAttribute = previousAttribute;
        }
    }


    @Override
    public void visit(PrintRecord node) {
        getExpressionType(node.expression());
    }


    @Override
    public void visit(WhileRecord node) {
        SemanticType conditionType = getExpressionType(node.expression());

        if (!isBoolean(conditionType)) {

            throw new SemanticException(
                    "A condição do while deve ser do tipo boolean.",
                    node.line(),
                    node.column());
        }

        loopDepth++;
        Set<VariableSymbolRecord> assignedBeforeLoop = definitelyAssignedVariables;
        definitelyAssignedVariables = new HashSet<>(assignedBeforeLoop);
        try {
            node.block().accept(this);
        } finally {
            definitelyAssignedVariables = assignedBeforeLoop;
            loopDepth--;
        }
    }

    @Override
    public void visit(ForRecord node) {
        enterScope();

        try {
            if (node.initializer() != null) {
                node.initializer().accept(this);
            }

            if (node.condition() != null) {
                SemanticType conditionType = getExpressionType(node.condition());
                if (!isBoolean(conditionType)) {
                    throw new SemanticException(
                            "A condição do for deve ser do tipo boolean.",
                            node.line(),
                            node.column());
                }
            }

            Set<VariableSymbolRecord> assignedBeforeLoop = definitelyAssignedVariables;
            definitelyAssignedVariables = new HashSet<>(assignedBeforeLoop);
            loopDepth++;
            try {
                node.block().accept(this);
                if (node.update() != null) {
                    node.update().accept(this);
                }
            } finally {
                definitelyAssignedVariables = assignedBeforeLoop;
                loopDepth--;
            }
        } finally {
            exitScope();
        }
    }


    @Override
    public void visit(BinaryExpressionRecord node) {
        getExpressionType(node);
    }


    @Override
    public void visit(VariableRecord node) {
        getExpressionType(node);
    }


    @Override
    public void visit(DeclarationRecord node) {

        validateTypeExists(
                node.type(),
                false,
                node.line(),
                node.column());

        if (resolvesToLocalVariable(node.name())) {
            throw new SemanticException(
                    "Variável '" + node.name()
                            + "' já declarada neste método.",
                    node.line(),
                    node.column());
        }

        SemanticType declaredType = convertType(node.type());

        VariableSymbolRecord symbol = new VariableSymbolRecord(
                node.name(),
                declaredType,
                node.line(),
                node.column());

        currentScope.define(symbol);

        if (node.value() != null) {
            SemanticType valueType = getExpressionType(node.value());

            if (valueType != null) {

                validateAssignableType(
                        declaredType,
                        valueType,
                        node.line(),
                        node.column());
            }
            definitelyAssignedVariables.add(symbol);
        }
    }


    @Override
    public void visit(LiteralRecord node) {
    }

    @Override
    public void visit(MemberAccessRecord node) {
        getExpressionType(node);
    }

    @Override
    public void visit(MemberCallRecord node) {
        getExpressionType(node);
    }

    @Override
    public void visit(NewObjectRecord node) {
        getExpressionType(node);
    }


    @Override
    public void visit(RegisterRecord node) {
        getExpressionType(node);
    }

    private void validateRegisterName(RegisterRecord register) {
        if (!register.name().matches("R[0-9]")) {
            throw new SemanticException(
                    "Registrador '" + register.name()
                            + "' inválido. Utilize R0 até R9.",
                    register.line(),
                    register.column());
        }
    }

    @Override
    public void visit(RegisterDeclarationRecord node) {
        RegisterRecord register = node.register();
        validateRegisterName(register);

        if (registers.containsKey(register.name())) {
            throw new SemanticException(
                    "O registrador '" + register.name()
                            + "' já foi declarado globalmente.",
                    register.line(),
                    register.column());
        }

        SemanticType inferredType = getExpressionType(node.value());
        if (inferredType.isNull()) {
            throw new SemanticException(
                    "Não é possível inferir o tipo do registrador a partir de 'null'.",
                    register.line(),
                    register.column());
        }
        registers.put(register.name(), inferredType);
    }


    @Override
    public void visit(ParameterRecord node) {

        validateTypeExists(
                node.type(),
                false,
                node.line(),
                node.column());

        SemanticType parameterType = convertType(node.type());

        VariableSymbolRecord symbol = new VariableSymbolRecord(
                node.name(),
                parameterType,
                node.line(),
                node.column());

        currentScope.define(symbol);
        definitelyAssignedVariables.add(symbol);
    }


    @Override
    public void visit(ReturnRecord node) {

        if (currentFunction == null) {
            throw new SemanticException(
                    "Comando return fora de uma função.",
                    node.line(),
                    node.column());
        }

        SemanticType expectedType = convertType(
                currentFunction.returnType());

        if (expectedType.primitiveType() == TokenType.VOID) {

            if (node.value() != null) {
                throw new SemanticException(
                        "Função void não pode retornar um valor.",
                        node.line(),
                        node.column());
            }

            return;
        }

        if (node.value() == null) {
            throw new SemanticException(
                    "Função deve retornar um valor do tipo "
                            + currentFunction.returnType().name() + ".",
                    node.line(),
                    node.column());
        }

        SemanticType receivedType = getExpressionType(node.value());

        if (receivedType == null) {
            throw new SemanticException(
                    "Não foi possível determinar o tipo do retorno.",
                    node.line(),
                    node.column());
        }

        validateAssignableType(
                expectedType,
                receivedType,
                node.line(),
                node.column());
    }


    @Override
    public void visit(CallRecord node) {
        resolveFunctionCall(node);
    }


    @Override
    public void visit(ExpressionStatementRecord node) {
        node.expression().accept(this);
    }


    @Override
    public void visit(PushRecord node) {
        node.register().accept(this);
    }


    @Override
    public void visit(PopRecord node) {
        node.register().accept(this);
    }


    @Override
    public void visit(InterfaceRecord node) {

        Set<String> signatures = new HashSet<>();
        String previousClass = currentClass;
        currentClass = node.name();

        try {
            for (FunctionRecord function : node.functions()) {
                if (!signatures.add(methodSignature(function))) {
                    throw new SemanticException(
                            "Assinatura duplicada na interface: '"
                                    + function.name() + "'.",
                            function.line(),
                            function.column());
                }

                function.accept(this);
            }
        } finally {
            currentClass = previousClass;
        }
    }

    private String methodSignature(FunctionRecord function) {
        StringBuilder signature = new StringBuilder(function.name()).append('(');

        for (ParameterRecord parameter : function.parameters()) {
            signature.append(parameter.type().name()).append(';');
        }

        return signature.append(')').toString();
    }


    @Override
    public void visit(SuperAttributeRecord node) {
        getExpressionType(node);
    }


    @Override
    public void visit(SuperCallRecord node) {
        resolveSuperCall(node);
    }


    @Override
    public void visit(ThisRecord node) {

        validateThisContext(
                node.line(),
                node.column());
    }


    @Override
    public void visit(ThisAttributeRecord node) {

        validateThisContext(
                node.line(),
                node.column());

        AttributeRecord attribute = findInheritedAttribute(
                currentClass,
                node.name());

        if (attribute == null) {
            throw new SemanticException(
                    "Atributo '" + node.name()
                            + "' não declarado na classe '"
                            + currentClass + "'.",
                    node.line(),
                    node.column());
        }

        ClassRecord declaringClass = findDeclaringClass(currentClass, node.name());

        validateMemberVisibility(
                attribute.visibility(),
                declaringClass == null ? null : declaringClass.name(),
                attribute.name(),
                node.line(),
                node.column());
    }

    @Override
    public void visit(ThisCallRecord node) {
        getExpressionType(node);
    }


    @Override
    public void visit(AssignmentRecord node) {
        VariableSymbolRecord localTarget = node.target() instanceof VariableRecord variable
                ? findLocalVariable(variable.name())
                : null;

        String assignedFieldName = null;
        if (node.target() instanceof ThisAttributeRecord attributeExpression) {
            assignedFieldName = attributeExpression.name();
        } else if (node.target() instanceof MemberAccessRecord memberAccess) {
            AttributeRecord attribute = resolveObjectAttribute(memberAccess);

            if (attribute.isFinal()) {
                throw new SemanticException(
                        "Não é permitido modificar o atributo final '"
                                + attribute.name() + "'.",
                        node.line(),
                        node.column());
            }
        } else if (node.target() instanceof VariableRecord variable
                && !resolvesToLocalVariable(variable.name())) {
            assignedFieldName = variable.name();
        }

        if (assignedFieldName != null) {
            AttributeRecord attribute = findInheritedAttribute(
                    currentClass,
                    assignedFieldName);

            if (attribute != null) {
                assignFinalField(attribute, node.line(), node.column());
            }
        }

        if (!(node.target() instanceof ThisAttributeRecord)
                && !(node.target() instanceof MemberAccessRecord)
                && !(node.target() instanceof VariableRecord)
                && !(node.target() instanceof RegisterRecord)) {
            throw new SemanticException(
                    "Destino de atribuição inválido.",
                    node.line(),
                    node.column());
        }

        SemanticType valueType = getExpressionType(node.value());
        SemanticType targetType = localTarget == null
                ? getExpressionType(node.target())
                : localTarget.type();

        if (targetType == null || valueType == null) {
            throw new SemanticException(
                    "Não foi possível determinar os tipos da atribuição.",
                    node.line(),
                    node.column());
        }

        if (node.target() instanceof RegisterRecord) {
            validateRegisterAssignment(
                    targetType,
                    valueType,
                    node.line(),
                    node.column());
        } else {
            validateAssignableType(
                    targetType,
                    valueType,
                    node.line(),
                    node.column());
        }
        if (localTarget != null) {
            definitelyAssignedVariables.add(localTarget);
        }
    }
}