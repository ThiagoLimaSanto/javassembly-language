package src.semantic;

import java.util.HashMap;
import java.util.HashSet;
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

    /** Verifica a existencia da classe pai e impede ciclos na heranca. */
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

    /** Valida os nomes informados no implements e evita repeticoes. */
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
        if (expression instanceof LiteralRecord literal) {
            TokenType type = literal.getType();
            if (type != null) {
                return convertType(type);
            }
        }
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
        return null;
    }

    @Override
    public void visit(ProgramRecord node) {
        // 1. Registrar todas as interfaces antes de validar referencias.
        for (InterfaceRecord interfaceNode : node.interfaces()) {
            if (interfaces.containsKey(interfaceNode.name())) {
                throw new SemanticException(
                        "Interface '" + interfaceNode.name() + "' ja declarada.",
                        interfaceNode.line(), interfaceNode.column());
            }
            interfaces.put(interfaceNode.name(), interfaceNode);
        }
        // 2. Registrar todas as classes.
        for (ClassRecord classNode : node.classes()) {
            if (classes.containsKey(classNode.name())) {
                throw new SemanticException(
                        "Classe '" + classNode.name() + "' ja declarada.",
                        classNode.line(), classNode.column());
            }
            classes.put(classNode.name(), classNode);
        }
        // 3. Uma classe e uma interface nao podem compartilhar o mesmo nome.
        for (ClassRecord classNode : node.classes()) {
            if (interfaces.containsKey(classNode.name())) {
                throw new SemanticException(
                        "Ja existe uma interface com o nome '" + classNode.name() + "'.",
                        classNode.line(), classNode.column());
            }
        }
        // 4. Validar a hierarquia e as interfaces implementadas.
        for (ClassRecord classNode : node.classes()) {
            validateClassInheritance(classNode);
            validateImplementedInterfaces(classNode);
        }
        // 5. Analisar as interfaces e, em seguida, as classes.
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
        enterScope();
        try {
            for (ParameterRecord parameter : node.parameters()) {
                parameter.accept(this);
            }
            if (node.block() != null) {
                for (StatementInterface statement : node.block().commands()) {
                    statement.accept(this);
                }
            }
        } finally {
            exitScope();
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
        // Nó folha.
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
        if (node.value() != null) {
            node.value().accept(this);
        }
    }

    @Override
    public void visit(CallRecord node) {
        // Implementar validação das funções e argumentos posteriormente.
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
        // Nó folha.
    }

    @Override
    public void visit(JMPRecord node) {
        // Nó folha.
    }

    @Override
    public void visit(JERecord node) {
        // Implementar posteriormente.
    }

    @Override
    public void visit(InterfaceRecord node) {
        for (FunctionRecord function : node.functions()) {
            function.accept(this);
        }
    }
}