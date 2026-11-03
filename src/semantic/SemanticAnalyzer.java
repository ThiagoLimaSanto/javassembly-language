package src.semantic;

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

    private Scope currentScope = new Scope(null);
    private final TypeChecker typeChecker = new TypeChecker();

    private void enterScope() {
        currentScope = new Scope(currentScope);
    }

    private void exitScope() {
        currentScope = currentScope.getParent();
    }

    private TokenType getExpressionType(ExpressionInterface expression) {

        if (expression instanceof LiteralRecord literal) {
            return literal.getType();
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

    private TokenType convertType(TypeRecord type) {
        return convertType(type.name());
    }

    private TokenType convertType(String type) {
        return TokenType.valueOf(type.toUpperCase());
    }

    private TokenType convertType(TokenType type) {
        return type;
    }

    @Override
    public void visit(ProgramRecord node) {
        for (ClassRecord classNode : node.classes()) {
            classNode.accept(this);
        }
    }

    @Override
    public void visit(ClassRecord node) {
        enterScope();

        try {
            for (AttributeRecord attribute : node.attributes()) {
                attribute.accept(this);
            }

            for (FunctionRecord function : node.functions()) {
                function.accept(this);
            }

        } finally {
            exitScope();
        }
    }

    @Override
    public void visit(FunctionRecord node) {
        enterScope();

        try {
            for (ParameterRecord parameter : node.parameters()) {
                parameter.accept(this);
            }

            for (StatementInterface statement : node.block().commands()) {
                statement.accept(this);
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

        TokenType declaredType = convertType(node.type());

        VariableSymbolRecord symbol = new VariableSymbolRecord(
                node.name(),
                declaredType,
                node.line(),
                node.column());

        currentScope.define(symbol);

        if (node.value() != null) {
            node.value().accept(this);

            TokenType valueType = getExpressionType(node.value());

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

        TokenType declaredType = convertType(node.type());

        VariableSymbolRecord symbol = new VariableSymbolRecord(
                node.name(),
                declaredType,
                node.line(),
                node.column());

        currentScope.define(symbol);

        if (node.value() != null) {

            node.value().accept(this);

            TokenType valueType = getExpressionType(node.value());

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
                    "Registrador '" + name + "' invalido. Utilize R0 ate R9.",
                    0,
                    0);
        }
    }

    @Override
    public void visit(ParameterRecord node) {

        VariableSymbolRecord symbol = new VariableSymbolRecord(
                node.name(),
                convertType(node.type()),
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
        // Implementar a validação dos argumentos posteriormente.
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
}