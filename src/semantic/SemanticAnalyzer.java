package src.semantic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import src.ast.ExpressionInterface;
import src.ast.StatementInterface;
import src.ast.records.AttributeRecord;
import src.ast.records.BinaryExpressionRecord;
import src.ast.records.BlockRecord;
import src.ast.records.CallRecord;
import src.ast.records.ClassRecord;
import src.ast.records.DeclarationRecord;
import src.ast.records.ExpressionStatementRecord;
import src.ast.records.FunctionRecord;
import src.ast.records.InterfaceRecord;
import src.ast.records.JERecord;
import src.ast.records.JMPRecord;
import src.ast.records.LabelRecord;
import src.ast.records.LiteralRecord;
import src.ast.records.ParameterRecord;
import src.ast.records.PopRecord;
import src.ast.records.PrintRecord;
import src.ast.records.ProgramRecord;
import src.ast.records.PushRecord;
import src.ast.records.RegisterRecord;
import src.ast.records.ReturnRecord;
import src.ast.records.TypeRecord;
import src.ast.records.VariableRecord;
import src.ast.records.VariableSymbolRecord;
import src.ast.records.WhileRecord;
import src.ast.visitor.ASTVisitor;
import src.exceptions.SemanticException;
import src.lexer.TokenType;

public class SemanticAnalyzer implements ASTVisitor {

    private VariableTable currentScope = new VariableTable(null);
    private final Map<String, FunctionTable> functionTables = new HashMap<>();
    private final Map<String, ClassRecord> classes = new HashMap<>();
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

        for (ClassRecord classNode : node.classes()) {

            if (classes.containsKey(classNode.name())) {
                throw new SemanticException(
                        "Classe '" + classNode.name() + "' ja declarada.",
                        classNode.line(),
                        classNode.column());
            }

            classes.put(classNode.name(), classNode);
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
                    0,
                    0);
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