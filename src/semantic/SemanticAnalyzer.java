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

    private String currentClass;
    private FunctionRecord currentFunction;

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

        String normalizedType = type.toUpperCase();

        return switch (normalizedType) {

            case "INT" ->
                SemanticType.primitive(TokenType.INT);

            case "DOUBLE" ->
                SemanticType.primitive(TokenType.DOUBLE);

            case "STRING" ->
                SemanticType.primitive(TokenType.STRING);

            case "BOOLEAN" ->
                SemanticType.primitive(TokenType.BOOLEAN);

            case "VOID" ->
                SemanticType.primitive(TokenType.VOID);

            default ->
                SemanticType.classType(normalizedType);
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

    private AttributeRecord findCurrentAttribute(String name) {
        return findAttribute(getCurrentClass(), name);
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

        if (currentFunction != null && currentFunction.isStatic()) {
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
                    0,
                    0);
        }

        List<SemanticType> argumentTypes = new ArrayList<>();

        // Descobre os tipos dos argumentos.
        for (ExpressionInterface argument : call.arguments()) {

            argument.accept(this);

            SemanticType argumentType = getExpressionType(argument);

            if (argumentType == null) {
                throw new SemanticException(
                        "Não foi possível determinar o tipo de um argumento.",
                        0,
                        0);
            }

            argumentTypes.add(argumentType);
        }

        String className = currentClass;

        boolean currentClassSearch = true;
        boolean functionFound = false;
        boolean sameParameterCount = false;

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

                    if (!expected.equals(received)) {
                        compatible = false;
                        break;
                    }
                }

                if (compatible) {
                    return function;
                }
            }

            className = classNode.superClass();
            currentClassSearch = false;
        }

        if (!functionFound) {
            throw new SemanticException(
                    "Função '" + call.name() + "' não declarada.",
                    0,
                    0);
        }

        if (!sameParameterCount) {
            throw new SemanticException(
                    "Quantidade de parâmetros incompatível na chamada da função '"
                            + call.name() + "'.",
                    0,
                    0);
        }

        throw new SemanticException(
                "Tipos dos argumentos incompatíveis na chamada da função '"
                        + call.name() + "'.",
                0,
                0);
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

        for (String interfaceName : classNode.interfaces()) {

            if (!interfaces.containsKey(interfaceName)) {
                throw new SemanticException(
                        "'" + interfaceName + "' não é uma interface declarada.",
                        classNode.line(),
                        classNode.column());
            }

            if (!implemented.add(interfaceName)) {
                throw new SemanticException(
                        "Interface '" + interfaceName
                                + "' implementada mais de uma vez.",
                        classNode.line(),
                        classNode.column());
            }
        }
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

        if (!inherited.returnType().equals(function.returnType())) {
            throw new SemanticException(
                    "Tipo de retorno incompatível na sobrescrita do método '"
                            + function.name() + "'.",
                    function.line(),
                    function.column());
        }

        if (inherited.visibility() == TokenType.PUBLIC
                && function.visibility() != TokenType.PUBLIC) {

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

            if (!firstType.equals(secondType)) {
                return false;
            }
        }

        return true;
    }

    // =========================================================
    // TIPOS DE EXPRESSÕES
    // =========================================================

    private SemanticType getExpressionType(ExpressionInterface expression) {

        // Literais
        if (expression instanceof LiteralRecord literal) {

            TokenType type = literal.getType();

            if (type != null) {
                return convertType(type);
            }
        }

        // Chamadas de funções
        if (expression instanceof CallRecord call) {

            FunctionRecord function = resolveFunctionCall(call);

            return convertType(function.returnType());
        }

        // Variáveis
        if (expression instanceof VariableRecord variable) {

            VariableSymbolRecord symbol = currentScope.resolve(
                    variable.name());

            if (symbol == null) {
                throw new SemanticException(
                        "Variável '" + variable.name() + "' não definida.",
                        variable.line(),
                        variable.column());
            }

            return symbol.type();
        }

        // This
        if (expression instanceof ThisRecord record) {

            validateThisContext(record.line(), record.column());

            return SemanticType.classType(currentClass);
        }

        // Atributo acessado por this
        if (expression instanceof ThisAttributeRecord attributeExpression) {

            validateThisContext(
                    attributeExpression.line(),
                    attributeExpression.column());

            AttributeRecord attribute = findCurrentAttribute(
                    attributeExpression.name());

            if (attribute == null) {
                throw new SemanticException(
                        "Atributo '" + attributeExpression.name()
                                + "' não declarado na classe '"
                                + currentClass + "'.",
                        attributeExpression.line(),
                        attributeExpression.column());
            }

            return convertType(attribute.type());
        }

        // Operações binárias
        if (expression instanceof BinaryExpressionRecord operation) {
            return validateOperation(operation);
        }

        return null;
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
                    0,
                    0);
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
                            0,
                            0);
                }

                if (left.primitiveType() == TokenType.DOUBLE
                        || right.primitiveType() == TokenType.DOUBLE) {

                    return SemanticType.primitive(TokenType.DOUBLE);
                }

                return SemanticType.primitive(TokenType.INT);

            // Igualdade e diferença
            case EQ:
            case NE:

                if (!areCompatible(left, right)) {
                    throw new SemanticException(
                            "Comparação exige operandos de tipos compatíveis.",
                            0,
                            0);
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
                            0,
                            0);
                }

                return SemanticType.primitive(TokenType.BOOLEAN);

            default:

                throw new SemanticException(
                        "Operador não suportado na análise semântica: "
                                + operator,
                        0,
                        0);
        }
    }

    private boolean isNumeric(SemanticType type) {

        if (type == null || !type.isPrimitive()) {
            return false;
        }

        return type.primitiveType() == TokenType.INT
                || type.primitiveType() == TokenType.DOUBLE;
    }

    private boolean areCompatible(
            SemanticType first,
            SemanticType second) {

        if (first.equals(second)) {
            return true;
        }

        return isNumeric(first) && isNumeric(second);
    }

    // =========================================================
    // PROGRAMA
    // =========================================================

    @Override
    public void visit(ProgramRecord node) {

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
        currentClass = node.name();

        FunctionTable functionTable = new FunctionTable();

        functionTables.put(currentClass, functionTable);

        try {

            // Analisa atributos
            for (AttributeRecord attribute : node.attributes()) {
                attribute.accept(this);
            }

            // Registra métodos e valida sobrescritas
            for (FunctionRecord function : node.functions()) {

                validateOverride(node, function);

                functionTable.define(function);
            }

            // Analisa métodos
            for (FunctionRecord function : node.functions()) {
                function.accept(this);
            }

        } finally {

            exitScope();
            currentClass = previousClass;
        }
    }

    // =========================================================
    // FUNÇÃO
    // =========================================================

    @Override
    public void visit(FunctionRecord node) {

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

        SemanticType declaredType = convertType(node.type());

        VariableSymbolRecord symbol = new VariableSymbolRecord(
                node.name(),
                declaredType,
                node.line(),
                node.column());

        currentScope.define(symbol);

        if (node.value() != null) {

            node.value().accept(this);

            SemanticType valueType = getExpressionType(node.value());

            if (valueType != null) {

                typeChecker.validateType(
                        declaredType,
                        valueType,
                        node.line(),
                        node.column());
            }
        }
    }

    // =========================================================
    // PRINT
    // =========================================================

    @Override
    public void visit(PrintRecord node) {
        node.expression().accept(this);
    }

    // =========================================================
    // WHILE
    // =========================================================

    @Override
    public void visit(WhileRecord node) {

        node.expression().accept(this);

        SemanticType conditionType = getExpressionType(node.expression());

        if (conditionType == null
                || conditionType.primitiveType() != TokenType.BOOLEAN) {

            throw new SemanticException(
                    "A condição do while deve ser do tipo boolean.",
                    0,
                    0);
        }

        node.block().accept(this);
    }

    // =========================================================
    // EXPRESSÕES BINÁRIAS
    // =========================================================

    @Override
    public void visit(BinaryExpressionRecord node) {

        node.left().accept(this);
        node.right().accept(this);
    }

    // =========================================================
    // VARIÁVEIS
    // =========================================================

    @Override
    public void visit(VariableRecord node) {

        VariableSymbolRecord symbol = currentScope.resolve(
                node.name());

        if (symbol == null) {
            throw new SemanticException(
                    "Variável '" + node.name() + "' não definida.",
                    node.line(),
                    node.column());
        }
    }

    // =========================================================
    // DECLARAÇÕES
    // =========================================================

    @Override
    public void visit(DeclarationRecord node) {

        SemanticType declaredType = convertType(node.type());

        VariableSymbolRecord symbol = new VariableSymbolRecord(
                node.name(),
                declaredType,
                node.line(),
                node.column());

        currentScope.define(symbol);

        if (node.value() != null) {

            node.value().accept(this);

            SemanticType valueType = getExpressionType(node.value());

            if (valueType != null) {

                typeChecker.validateType(
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

    // =========================================================
    // REGISTRADORES
    // =========================================================

    @Override
    public void visit(RegisterRecord node) {

        String name = node.name();

        if (!name.matches("R[0-9]")) {
            throw new SemanticException(
                    "Registrador '" + name
                            + "' inválido. Utilize R0 até R9.",
                    node.line(),
                    node.column());
        }
    }

    // =========================================================
    // PARÂMETROS
    // =========================================================

    @Override
    public void visit(ParameterRecord node) {

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
                    0,
                    0);
        }

        SemanticType expectedType = convertType(
                currentFunction.returnType());

        // Função void
        if (expectedType.primitiveType() == TokenType.VOID) {

            if (node.value() != null) {
                throw new SemanticException(
                        "Função void não pode retornar um valor.",
                        currentFunction.line(),
                        currentFunction.column());
            }

            return;
        }

        // Função não void
        if (node.value() == null) {
            throw new SemanticException(
                    "Função deve retornar um valor do tipo "
                            + currentFunction.returnType().name() + ".",
                    currentFunction.line(),
                    currentFunction.column());
        }

        node.value().accept(this);

        SemanticType receivedType = getExpressionType(node.value());

        if (receivedType == null) {
            throw new SemanticException(
                    "Não foi possível determinar o tipo do retorno.",
                    currentFunction.line(),
                    currentFunction.column());
        }

        typeChecker.validateType(
                expectedType,
                receivedType,
                currentFunction.line(),
                currentFunction.column());
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
    // LABEL
    // =========================================================

    @Override
    public void visit(LabelRecord node) {
    }

    // =========================================================
    // JMP
    // =========================================================

    @Override
    public void visit(JMPRecord node) {
    }

    // =========================================================
    // JE
    // =========================================================

    @Override
    public void visit(JERecord node) {
    }

    // =========================================================
    // INTERFACE
    // =========================================================

    @Override
    public void visit(InterfaceRecord node) {

        for (FunctionRecord function : node.functions()) {
            function.accept(this);
        }
    }

    // =========================================================
    // SUPER ATTRIBUTE
    // =========================================================

    @Override
    public void visit(SuperAttributeRecord node) {
        // Implementação da validação de super será feita separadamente.
    }

    // =========================================================
    // SUPER CALL
    // =========================================================

    @Override
    public void visit(SuperCallRecord node) {
        // Implementação da validação de super será feita separadamente.
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

        AttributeRecord attribute = findCurrentAttribute(
                node.name());

        if (attribute == null) {
            throw new SemanticException(
                    "Atributo '" + node.name()
                            + "' não declarado na classe '"
                            + currentClass + "'.",
                    node.line(),
                    node.column());
        }
    }

    // =========================================================
    // ASSIGNMENT
    // =========================================================

    @Override
    public void visit(AssignmentRecord node) {

        // Primeiro valida o destino.
        node.target().accept(this);

        // Verifica se o destino é um atributo final.
        if (node.target() instanceof ThisAttributeRecord attributeExpression) {

            AttributeRecord attribute = findCurrentAttribute(
                    attributeExpression.name());

            if (attribute != null && attribute.isFinal()) {
                throw new SemanticException(
                        "Não é permitido modificar o atributo final '"
                                + attribute.name() + "'.",
                        node.line(),
                        node.column());
            }
        }

        // Valida a expressão do lado direito.
        node.value().accept(this);

        SemanticType targetType = getExpressionType(node.target());
        SemanticType valueType = getExpressionType(node.value());

        if (targetType == null || valueType == null) {
            throw new SemanticException(
                    "Não foi possível determinar os tipos da atribuição.",
                    node.line(),
                    node.column());
        }

        // Verifica compatibilidade.
        typeChecker.validateType(
                targetType,
                valueType,
                node.line(),
                node.column());
    }
}