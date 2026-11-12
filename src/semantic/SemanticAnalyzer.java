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

    private final TypeChecker typeChecker = new TypeChecker();

    // =========================================================
    // ESCOPO
    // =========================================================

    private void enterScope() {
        currentScope = new VariableTable(currentScope);
    }

    private void exitScope() {
        currentScope = currentScope.getParent();
    }

    // =========================================================
    // TIPOS
    // =========================================================

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
        return SemanticType.primitive(type);
    }

    // =========================================================
    // CLASSE ATUAL
    // =========================================================

    private ClassRecord getCurrentClass() {

        if (currentClass == null) {
            return null;
        }

        return classes.get(currentClass);
    }

    // =========================================================
    // ATRIBUTOS
    // =========================================================

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
        VariableTable scope = currentScope;

        while (scope != null && scope != classScope) {
            VariableSymbolRecord local = scope.resolveCurrent(name);

            if (local != null) {
                return local;
            }

            scope = scope.getParent();
        }

        AttributeRecord attribute = findInheritedAttribute(currentClass, name);

        if (attribute != null) {
            ClassRecord declaringClass = findDeclaringClass(currentClass, name);

            if (attribute.visibility() == TokenType.PRIVATE
                    && (declaringClass == null
                            || !declaringClass.name().equals(currentClass))) {
                throw new SemanticException(
                        "O atributo privado '" + name
                                + "' não é acessível nesta classe.",
                        line,
                        column);
            }

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

    private boolean resolvesToLocalVariable(String name) {
        VariableTable scope = currentScope;

        while (scope != null && scope != classScope) {
            if (scope.resolveCurrent(name) != null) {
                return true;
            }

            scope = scope.getParent();
        }

        return false;
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
            for (FunctionRecord function : interfaceNode.functions()) {
                if (matchesCall(function, call.name(), argumentTypes)) {
                    return function;
                }
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
        Set<String> visited = new HashSet<>();

        while (className != null && visited.add(className)) {
            ClassRecord classNode = classes.get(className);

            if (classNode == null) {
                break;
            }

            for (FunctionRecord function : classNode.functions()) {
                if (!function.name().equals(call.name())
                        || function.visibility() == TokenType.PRIVATE) {
                    continue;
                }

                methodNameFound = true;

                if (function.parameters().size() != argumentTypes.size()) {
                    continue;
                }
                matchingArityFound = true;

                if (matchesCall(function, call.name(), argumentTypes)) {
                    validateMemberVisibility(
                            function.visibility(),
                            classNode.name(),
                            call.name(),
                            call.line(),
                            call.column());
                    return function;
                }
            }

            className = classNode.superClass();
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

    private void validateMemberVisibility(
            TokenType visibility,
            String declaringClass,
            String memberName,
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

        // All top-level types currently share the same implicit package.
    }

    private void validateTypeExists(
            TypeRecord type,
            boolean allowVoid,
            int line,
            int column) {
        SemanticType semanticType = convertType(type);

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

        if (!classes.containsKey(semanticType.className())
                && !interfaces.containsKey(semanticType.className())) {
            throw new SemanticException(
                    "Tipo '" + semanticType.className() + "' não declarado.",
                    line,
                    column);
        }
    }

    // =========================================================
    // VALIDAÇÃO DO THIS
    // =========================================================

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

    // =========================================================
    // CHAMADAS DE FUNÇÕES
    // =========================================================

    private FunctionRecord resolveFunctionCall(CallRecord call) {

        if (currentClass == null) {
            throw new SemanticException(
                    "Chamada de função fora de uma classe.",
                    call.line(),
                    call.column());
        }

        List<SemanticType> argumentTypes = new ArrayList<>();

        // Descobre os tipos dos argumentos.
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

        boolean currentClassSearch = true;
        boolean functionFound = false;
        boolean sameParameterCount = false;
        boolean instanceMethodInStaticContext = false;

        Set<String> visited = new HashSet<>();

        // Procura na classe atual e nas classes ancestrais.
        while (className != null && visited.add(className)) {

            ClassRecord classNode = classes.get(className);

            if (classNode == null) {
                break;
            }

            for (FunctionRecord function : classNode.functions()) {

                if (!function.name().equals(call.name())) {
                    continue;
                }

                // Métodos privados não são acessíveis pela subclasse.
                if (!currentClassSearch
                        && function.visibility() == TokenType.PRIVATE) {
                    continue;
                }

                functionFound = true;

                if (function.parameters().size() != argumentTypes.size()) {
                    continue;
                }

                sameParameterCount = true;

                boolean compatible = true;

                for (int i = 0; i < argumentTypes.size(); i++) {

                    SemanticType expected = convertType(
                            function.parameters().get(i).type());

                    SemanticType received = argumentTypes.get(i);

                    if (!isAssignable(expected, received)) {
                        compatible = false;
                        break;
                    }
                }

                if (compatible) {
                    if (isStaticContext() && !function.isStatic()) {
                        instanceMethodInStaticContext = true;
                        continue;
                    }

                    return function;
                }
            }

            className = classNode.superClass();
            currentClassSearch = false;
        }

        if (!functionFound) {
            throw new SemanticException(
                    "Função '" + call.name() + "' não declarada.",
                    call.line(),
                    call.column());
        }

        if (instanceMethodInStaticContext) {
            throw new SemanticException(
                    "Um método de instância não pode ser chamado de um contexto estático.",
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
                if (attribute.visibility() == TokenType.PRIVATE) {
                    throw new SemanticException(
                    "O atributo '" + access.name()
                            + "' é privado na classe pai.",
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
        Set<String> visited = new HashSet<>();

        while (superClass != null && visited.add(superClass)) {
            ClassRecord parent = classes.get(superClass);

            if (parent == null) {
                break;
            }

            for (FunctionRecord function : parent.functions()) {
                if (!function.name().equals(call.name())
                || function.visibility() == TokenType.PRIVATE) {
                    continue;
                }

                methodFound = true;

                if (function.parameters().size() != argumentTypes.size()) {
                    continue;
                }

                sameParameterCount = true;
                boolean compatible = true;

                for (int i = 0; i < argumentTypes.size(); i++) {
                    SemanticType expected = convertType(
                    function.parameters().get(i).type());

                    if (!isAssignable(expected, argumentTypes.get(i))) {
                compatible = false;
                break;
                    }
                }

                if (compatible) {
                    return function;
                }
            }

            superClass = parent.superClass();
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

    // =========================================================
    // HERANÇA
    // =========================================================

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

                if (parentFunction.visibility() == TokenType.PRIVATE) {
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

            current = parent;
        }
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

                for (FunctionRecord requirement : interfaceNode.functions()) {
                    FunctionRecord implementation = findClassMethod(
                            classNode.name(),
                            requirement);

                    if (implementation == null) {
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

                    if (!isAssignable(expectedReturn, actualReturn)) {
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

    // =========================================================
    // SOBRESCRITA DE MÉTODOS
    // =========================================================

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

        if (!isAssignable(
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

    // =========================================================
    // TIPOS DE EXPRESSÕES
    // =========================================================

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

            if (attribute.visibility() == TokenType.PRIVATE
                    && (declaringClass == null
                            || !declaringClass.name().equals(currentClass))) {
                throw new SemanticException(
                        "O atributo privado '" + attribute.name()
                                + "' não é acessível nesta classe.",
                        attributeExpression.line(),
                        attributeExpression.column());
            }

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

    // =========================================================
    // VALIDAÇÃO DE OPERAÇÕES
    // =========================================================

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

            // Operações aritméticas
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

            // Igualdade e diferença
            case EQ:
            case NE:

                if (!areCompatible(left, right)) {
                    throw new SemanticException(
                            "Comparação exige operandos de tipos compatíveis.",
                            operation.line(),
                            operation.column());
                }

                return SemanticType.primitive(TokenType.BOOLEAN);

            // Comparações numéricas
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

        return isNumeric(first) && isNumeric(second);
    }

    private boolean isAssignable(
            SemanticType expected,
            SemanticType received) {
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

    // =========================================================
    // PROGRAMA
    // =========================================================

    @Override
    public void visit(ProgramRecord node) {

        for (RegisterDeclarationRecord register : node.registers()) {
            register.accept(this);
        }

        // Registra interfaces
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

        // Registra classes
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

        // Verifica colisões entre classes e interfaces
        for (ClassRecord classNode : node.classes()) {

            if (interfaces.containsKey(classNode.name())) {
                throw new SemanticException(
                        "Já existe uma interface com o nome '"
                                + classNode.name() + "'.",
                        classNode.line(),
                        classNode.column());
            }
        }

        // Valida herança e interfaces implementadas
        for (ClassRecord classNode : node.classes()) {

            validateClassInheritance(classNode);
            validateImplementedInterfaces(classNode);
        }

        // Analisa interfaces
        for (InterfaceRecord interfaceNode : node.interfaces()) {
            interfaceNode.accept(this);
        }

        // Analisa classes
        for (ClassRecord classNode : node.classes()) {
            classNode.accept(this);
        }
    }

    // =========================================================
    // CLASSE
    // =========================================================

    @Override
    public void visit(ClassRecord node) {

        enterScope();

        String previousClass = currentClass;
        VariableTable previousClassScope = classScope;
        currentClass = node.name();
        classScope = currentScope;

        FunctionTable functionTable = new FunctionTable();

        functionTables.put(currentClass, functionTable);

        try {

            // Registra métodos e valida sobrescritas
            for (FunctionRecord function : node.functions()) {

                validateOverride(node, function);

                functionTable.define(function);
            }

            for (AttributeRecord attribute : node.attributes()) {
                attribute.accept(this);
            }

            // Analisa métodos
            for (FunctionRecord function : node.functions()) {
                function.accept(this);
            }

        } finally {

            exitScope();
            currentClass = previousClass;
            classScope = previousClassScope;
        }
    }

    // =========================================================
    // FUNÇÃO
    // =========================================================

    @Override
    public void visit(FunctionRecord node) {

        validateTypeExists(
                node.returnType(),
                true,
                node.line(),
                node.column());

        FunctionRecord previousFunction = currentFunction;

        currentFunction = node;

        enterScope();

        try {

            for (ParameterRecord parameter : node.parameters()) {
                parameter.accept(this);
            }

            if (node.block() != null) {
                node.block().accept(this);
            }

        } finally {

            exitScope();
            currentFunction = previousFunction;
        }
    }

    // =========================================================
    // BLOCO
    // =========================================================

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

    // =========================================================
    // ATRIBUTO
    // =========================================================

    @Override
    public void visit(AttributeRecord node) {

        validateTypeExists(
                node.type(),
                false,
                node.line(),
                node.column());

        if (node.isFinal() && node.value() == null) {
            throw new SemanticException(
                    "O atributo final '" + node.name()
                            + "' deve ser inicializado na declaração; "
                            + "construtores ainda não estão disponíveis.",
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

    // =========================================================
    // PRINT
    // =========================================================

    @Override
    public void visit(PrintRecord node) {
        getExpressionType(node.expression());
    }

    // =========================================================
    // WHILE
    // =========================================================

    @Override
    public void visit(WhileRecord node) {
        SemanticType conditionType = getExpressionType(node.expression());

        if (!isBoolean(conditionType)) {

            throw new SemanticException(
                    "A condição do while deve ser do tipo boolean.",
                    node.line(),
                    node.column());
        }

        node.block().accept(this);
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

            node.block().accept(this);

            if (node.update() != null) {
                node.update().accept(this);
            }
        } finally {
            exitScope();
        }
    }

    // =========================================================
    // EXPRESSÕES BINÁRIAS
    // =========================================================

    @Override
    public void visit(BinaryExpressionRecord node) {
        getExpressionType(node);
    }

    // =========================================================
    // VARIÁVEIS
    // =========================================================

    @Override
    public void visit(VariableRecord node) {
        getExpressionType(node);
    }

    // =========================================================
    // DECLARAÇÕES
    // =========================================================

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
        }
    }

    // =========================================================
    // LITERAIS
    // =========================================================

    @Override
    public void visit(LiteralRecord node) {
        // Literais não precisam de validação adicional.
    }

    @Override
    public void visit(MemberAccessRecord node) {
        getExpressionType(node);
    }

    @Override
    public void visit(MemberCallRecord node) {
        getExpressionType(node);
    }

    // =========================================================
    // REGISTRADORES
    // =========================================================

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

        SemanticType inferredType = getExpressionType(node.value());
        SemanticType declaredType = registers.putIfAbsent(
                register.name(),
                inferredType);

        if (declaredType != null) {
            validateRegisterAssignment(
                    declaredType,
                    inferredType,
                    register.line(),
                    register.column());
        }
    }

    // =========================================================
    // PARÂMETROS
    // =========================================================

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
    }

    // =========================================================
    // RETURN
    // =========================================================

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

        // Função void
        if (expectedType.primitiveType() == TokenType.VOID) {

            if (node.value() != null) {
                throw new SemanticException(
                        "Função void não pode retornar um valor.",
                        node.line(),
                        node.column());
            }

            return;
        }

        // Função não void
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

    // =========================================================
    // CHAMADAS
    // =========================================================

    @Override
    public void visit(CallRecord node) {
        resolveFunctionCall(node);
    }

    // =========================================================
    // EXPRESSÕES COMO COMANDOS
    // =========================================================

    @Override
    public void visit(ExpressionStatementRecord node) {
        node.expression().accept(this);
    }

    // =========================================================
    // PUSH
    // =========================================================

    @Override
    public void visit(PushRecord node) {
        node.register().accept(this);
    }

    // =========================================================
    // POP
    // =========================================================

    @Override
    public void visit(PopRecord node) {
        node.register().accept(this);
    }

    // =========================================================
    // INTERFACE
    // =========================================================

    @Override
    public void visit(InterfaceRecord node) {

        Set<String> signatures = new HashSet<>();

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
    }

    private String methodSignature(FunctionRecord function) {
        StringBuilder signature = new StringBuilder(function.name()).append('(');

        for (ParameterRecord parameter : function.parameters()) {
            signature.append(parameter.type().name()).append(';');
        }

        return signature.append(')').toString();
    }

    // =========================================================
    // SUPER ATTRIBUTE
    // =========================================================

    @Override
    public void visit(SuperAttributeRecord node) {
        getExpressionType(node);
    }

    // =========================================================
    // SUPER CALL
    // =========================================================

    @Override
    public void visit(SuperCallRecord node) {
        resolveSuperCall(node);
    }

    // =========================================================
    // THIS
    // =========================================================

    @Override
    public void visit(ThisRecord node) {

        validateThisContext(
                node.line(),
                node.column());
    }

    // =========================================================
    // THIS ATTRIBUTE
    // =========================================================

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

        if (attribute.visibility() == TokenType.PRIVATE
                && (declaringClass == null
                        || !declaringClass.name().equals(currentClass))) {
            throw new SemanticException(
                    "O atributo privado '" + attribute.name()
                            + "' não é acessível nesta classe.",
                    node.line(),
                    node.column());
        }
    }

    @Override
    public void visit(ThisCallRecord node) {
        getExpressionType(node);
    }

    // =========================================================
    // ASSIGNMENT
    // =========================================================

    @Override
    public void visit(AssignmentRecord node) {

        // Verifica se o destino é um atributo final.
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

            if (attribute != null && attribute.isFinal()) {
                throw new SemanticException(
                        "Não é permitido modificar o atributo final '"
                                + attribute.name() + "'.",
                        node.line(),
                        node.column());
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

        SemanticType targetType = getExpressionType(node.target());
        SemanticType valueType = getExpressionType(node.value());

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
    }
}