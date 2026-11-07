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
        String normalizedType = type.toUpperCase();
        switch (normalizedType) {
            case "INT":
                return SemanticType.primitive(TokenType.INT);
            case "DOUBLE":
                return SemanticType.primitive(TokenType.DOUBLE);
            case "STRING":
                return SemanticType.primitive(TokenType.STRING);
            case "BOOLEAN":
                return SemanticType.primitive(TokenType.BOOLEAN);
            default:
                return SemanticType.classType(normalizedType);
        }
    }

    private FunctionRecord resolveFunctionCall(CallRecord call) {

        if (currentClass == null) {
            throw new SemanticException(
                    "Chamada de funcao fora de uma classe.",
                    0,
                    0);
        }

        // Descobre os tipos dos argumentos.
        List<SemanticType> argumentTypes = new ArrayList<>();

        for (ExpressionInterface argument : call.arguments()) {

            argument.accept(this);

            SemanticType argumentType = getExpressionType(argument);

            if (argumentType == null) {
                throw new SemanticException(
                        "Nao foi possivel determinar o tipo de um argumento.",
                        0,
                        0);
            }

            argumentTypes.add(argumentType);
        }

        String className = currentClass;
        boolean currentClassSearch = true;

        boolean functionFound = false;
        boolean sameParameterCount = false;

        // Procura na classe atual e nas classes ancestrais.
        while (className != null) {

            ClassRecord classNode = classes.get(className);

            if (classNode == null) {
                break;
            }

            for (FunctionRecord function : classNode.functions()) {

                if (!function.name().equals(call.name())) {
                    continue;
                }

                // Métodos privados não são herdados.
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
                    "Funcao '" + call.name() + "' nao declarada.",
                    0,
                    0);
        }

        if (!sameParameterCount) {
            throw new SemanticException(
                    "Nao existe esses parametros para a funcao '"
                            + call.name() + "'.",
                    0,
                    0);
        }

        throw new SemanticException(
                "Tipos dos argumentos incompatíveis na chamada da funcao '"
                        + call.name() + "'.",
                0,
                0);
    }

    private SemanticType convertType(TokenType type) {
        return SemanticType.primitive(type);
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
                        "Heranca circular detectada envolvendo a classe '"
                                + current.name() + "'.",
                        current.line(), current.column());
            }
            if (current.superClass() == null) {
                break;
            }
            ClassRecord parent = classes.get(current.superClass());
            if (parent == null) {
                throw new SemanticException(
                        "Classe pai '" + current.superClass() + "' nao declarada.",
                        current.line(), current.column());
            }
            current = parent;
        }
    }

    private void validateImplementedInterfaces(ClassRecord classNode) {
        Set<String> implemented = new HashSet<>();
        for (String interfaceName : classNode.interfaces()) {
            if (!interfaces.containsKey(interfaceName)) {
                throw new SemanticException(
                        "'" + interfaceName + "' nao e uma interface declarada.",
                        classNode.line(), classNode.column());
            }
            if (!implemented.add(interfaceName)) {
                throw new SemanticException(
                        "Interface '" + interfaceName + "' implementada mais de uma vez.",
                        classNode.line(), classNode.column());
            }
        }
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
                    "O metodo '" + function.name()
                            + "' nao pode ser sobrescrito porque e final.",
                    function.line(),
                    function.column());
        }
        if (inherited.returnType() != function.returnType()) {
            throw new SemanticException(
                    "Tipo de retorno incompatível na sobrescrita do metodo '"
                            + function.name() + "'.",
                    function.line(),
                    function.column());
        }
        if (inherited.visibility() == TokenType.PUBLIC
                && function.visibility() != TokenType.PUBLIC) {
            throw new SemanticException(
                    "O metodo '" + function.name()
                            + "' nao pode reduzir sua visibilidade.",
                    function.line(),
                    function.column());
        }
        if (inherited.isStatic() != function.isStatic()) {
            throw new SemanticException(
                    "O metodo '" + function.name()
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
            if (first.parameters().get(i).type() != second.parameters().get(i).type()) {
                return false;
            }
        }
        return true;
    }

    private SemanticType getExpressionType(ExpressionInterface expression) {

        // Literais
        if (expression instanceof LiteralRecord literal) {
            TokenType type = literal.getType();

            if (type != null) {
                return convertType(type);
            }
        }

        if (expression instanceof CallRecord call) {
            FunctionRecord function = resolveFunctionCall(call);

            return convertType(function.returnType());
        }

        // Variáveis
        if (expression instanceof VariableRecord variable) {
            VariableSymbolRecord symbol = currentScope.resolve(variable.name());

            if (symbol == null) {
                throw new SemanticException(
                        "Variavel '" + variable.name() + "' nao definida.",
                        variable.line(),
                        variable.column());
            }

            return symbol.type();
        }

        // Operações binárias
        if (expression instanceof BinaryExpressionRecord operation) {
            return validateOperation(operation);
        }

        return null;
    }

    private SemanticType validateOperation(BinaryExpressionRecord operation) {

        SemanticType left = getExpressionType(operation.left());
        SemanticType right = getExpressionType(operation.right());

        if (left == null || right == null) {
            throw new SemanticException(
                    "Nao foi possivel determinar o tipo dos operandos.",
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
                            "Operacao aritmetica exige operandos numericos.",
                            0,
                            0);
                }

                // Se algum operando for double, o resultado será double.
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
                            "Comparacao exige operandos de tipos compativeis.",
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
                            "Comparacao exige operandos numericos.",
                            0,
                            0);
                }

                return SemanticType.primitive(TokenType.BOOLEAN);

            default:
                throw new SemanticException(
                        "Operador nao suportado na analise semantica: " + operator,
                        0,
                        0);
        }
    }

    private boolean isNumeric(SemanticType type) {

        if (!type.isPrimitive()) {
            return false;
        }

        return type.primitiveType() == TokenType.INT
                || type.primitiveType() == TokenType.DOUBLE;
    }

    private boolean areCompatible(SemanticType first, SemanticType second) {

        // Tipos iguais
        if (first.equals(second)) {
            return true;
        }

        // int e double podem ser comparados entre si.
        return isNumeric(first) && isNumeric(second);
    }

    @Override
    public void visit(ProgramRecord node) {
        for (InterfaceRecord interfaceNode : node.interfaces()) {
            if (interfaces.containsKey(interfaceNode.name())) {
                throw new SemanticException(
                        "Interface '" + interfaceNode.name() + "' ja declarada.",
                        interfaceNode.line(), interfaceNode.column());
            }
            interfaces.put(interfaceNode.name(), interfaceNode);
        }
        for (ClassRecord classNode : node.classes()) {
            if (classes.containsKey(classNode.name())) {
                throw new SemanticException(
                        "Classe '" + classNode.name() + "' ja declarada.",
                        classNode.line(), classNode.column());
            }
            classes.put(classNode.name(), classNode);
        }
        for (ClassRecord classNode : node.classes()) {
            if (interfaces.containsKey(classNode.name())) {
                throw new SemanticException(
                        "Ja existe uma interface com o nome '" + classNode.name() + "'.",
                        classNode.line(), classNode.column());
            }
        }
        for (ClassRecord classNode : node.classes()) {
            validateClassInheritance(classNode);
            validateImplementedInterfaces(classNode);
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
        currentClass = node.name();
        FunctionTable functionTable = new FunctionTable();
        functionTables.put(currentClass, functionTable);
        try {
            for (AttributeRecord attribute : node.attributes()) {
                attribute.accept(this);
            }
            for (FunctionRecord function : node.functions()) {
                validateOverride(node, function);
                functionTable.define(function);
            }
            for (FunctionRecord function : node.functions()) {
                function.accept(this);
            }
        } finally {
            exitScope();
            currentClass = previousClass;
        }
    }

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

    @Override
    public void visit(PrintRecord node) {
        node.expression().accept(this);
    }

    @Override
    public void visit(WhileRecord node) {
        node.expression().accept(this);
        node.block().accept(this);
    }

    @Override
    public void visit(BinaryExpressionRecord node) {
        node.left().accept(this);
        node.right().accept(this);
    }

    @Override
    public void visit(VariableRecord node) {
        VariableSymbolRecord symbol = currentScope.resolve(node.name());
        if (symbol == null) {
            throw new SemanticException(
                    "Variavel '" + node.name() + "' nao definida.",
                    node.line(),
                    node.column());
        }
    }

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

    @Override
    public void visit(LiteralRecord node) {
    }

    @Override
    public void visit(RegisterRecord node) {
        String name = node.name();
        if (!name.matches("R[0-9]")) {
            throw new SemanticException(
                    "Registrador '" + name +
                            "' invalido. Utilize R0 ate R9.",
                    node.line(),
                    node.column());
        }
    }

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

    @Override
    public void visit(ReturnRecord node) {

        if (currentFunction == null) {
            throw new SemanticException(
                    "Comando return fora de uma funcao.",
                    0,
                    0);
        }

        SemanticType expectedType = convertType(currentFunction.returnType());

        if (expectedType.primitiveType() == TokenType.VOID) {

            if (node.value() != null) {
                throw new SemanticException(
                        "Funcao void nao pode retornar um valor.",
                        currentFunction.line(),
                        currentFunction.column());
            }

            return;
        }

        if (node.value() == null) {
            throw new SemanticException(
                    "Funcao deve retornar um valor do tipo "
                            + expectedType.primitiveType(),
                    currentFunction.line(),
                    currentFunction.column());
        }

        node.value().accept(this);

        SemanticType receivedType = getExpressionType(node.value());

        if (receivedType == null) {
            throw new SemanticException(
                    "Nao foi possivel determinar o tipo do retorno.",
                    currentFunction.line(),
                    currentFunction.column());
        }

        typeChecker.validateType(
                expectedType,
                receivedType,
                currentFunction.line(),
                currentFunction.column());
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
    public void visit(LabelRecord node) {
    }

    @Override
    public void visit(JMPRecord node) {
    }

    @Override
    public void visit(JERecord node) {
    }

    @Override
    public void visit(InterfaceRecord node) {
        for (FunctionRecord function : node.functions()) {
            function.accept(this);
        }
    }
}