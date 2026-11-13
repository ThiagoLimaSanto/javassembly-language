package src.ast.visitor;

import src.ast.ASTNode;
import src.ast.ExpressionInterface;
import src.ast.records.AssignmentRecord;
import src.ast.records.ArrayAccessRecord;
import src.ast.records.ArrayCreationRecord;
import src.ast.records.ArrayInitializerRecord;
import src.ast.records.AttributeRecord;
import src.ast.records.BinaryExpressionRecord;
import src.ast.records.BlockRecord;
import src.ast.records.CallRecord;
import src.ast.records.ClassRecord;
import src.ast.records.ConstructorRecord;
import src.ast.records.DeclarationRecord;
import src.ast.records.ExpressionStatementRecord;
import src.ast.records.ForRecord;
import src.ast.records.FunctionRecord;
import src.ast.records.IfRecord;
import src.ast.records.InterfaceRecord;
import src.ast.records.LiteralRecord;
import src.ast.records.MemberAccessRecord;
import src.ast.records.MemberCallRecord;
import src.ast.records.NewObjectRecord;
import src.ast.records.ParameterRecord;
import src.ast.records.PrintRecord;
import src.ast.records.ProgramRecord;
import src.ast.records.RegisterRecord;
import src.ast.records.RegisterDeclarationRecord;
import src.ast.records.ReturnRecord;
import src.ast.records.SuperAttributeRecord;
import src.ast.records.SuperCallRecord;
import src.ast.records.ThisAttributeRecord;
import src.ast.records.ThisCallRecord;
import src.ast.records.ThisRecord;
import src.ast.records.UnaryExpressionRecord;
import src.ast.records.VariableRecord;
import src.ast.records.WhileRecord;

public class ASTPrinter implements ASTVisitor {
    private int indentation = 0;

    private void printLine(String text) {
        System.out.println("    ".repeat(indentation) + text);
    }

    private void visitChild(ASTNode node) {
        indentation++;

        try {
            node.accept(this);
        } finally {
            indentation--;
        }
    }

    private void visitNamedChild(String label, ASTNode node) {
        indentation++;
        try {
            printLine(label);
            visitChild(node);
        } finally {
            indentation--;
        }
    }

    @Override
    public void visit(ProgramRecord node) {
        printLine("Programa");

        for (InterfaceRecord interfaceNode : node.interfaces()) {
            visitChild(interfaceNode);
        }

        for (RegisterDeclarationRecord register : node.registers()) {
            visitChild(register);
        }

        for (ClassRecord classNode : node.classes()) {
            visitChild(classNode);
        }
    }

    @Override
    public void visit(ClassRecord node) {
        printLine("Classe: " + node.name()
                + (node.isAbstract() ? " [ABSTRACT]" : "")
                + (node.isFinal() ? " [FINAL]" : ""));

        if (node.superClass() != null) {
            printLine("  Extends: " + node.superClass());
        }

        if (!node.interfaces().isEmpty()) {
            printLine("  Implements: " + String.join(", ", node.interfaces()));
        }

        printLine("  Atributos:");
        for (AttributeRecord attribute : node.attributes()) {
            attribute.accept(this);
        }

        printLine("  Funções:");
        for (FunctionRecord function : node.functions()) {
            function.accept(this);
        }

        printLine("Construtores:");
        for (ConstructorRecord constructor : node.constructors()) {
            constructor.accept(this);
        }
    }

    @Override
    public void visit(ConstructorRecord node) {
        printLine("Construtor [" + (node.visibility() == null ? "PACKAGE" : node.visibility()) + "]");
        for (ParameterRecord parameter : node.parameters()) {
            visitChild(parameter);
        }
        if (node.invocation() != null) {
            printLine(node.invocation().delegatesToThis()
                    ? "Delegação this(...)"
                    : "Chamada super(...)");
            for (ExpressionInterface argument : node.invocation().arguments()) {
                visitChild(argument);
            }
        }
        visitChild(node.block());
    }

    @Override
    public void visit(FunctionRecord node) {
        String visibility = node.visibility() == null
                ? " [PACKAGE]"
                : " [" + node.visibility() + "]";

        printLine("Função " + node.name() + " → " + node.returnType() + visibility
                + (node.isStatic() ? " [STATIC]" : "")
                + (node.isFinal() ? " [FINAL]" : "")
                + (node.isAbstract() ? " [ABSTRACT]" : ""));

        for (ParameterRecord parameter : node.parameters()) {
            visitChild(parameter);
        }

        if (node.block() != null) {
            visitChild(node.block());
        } else {
            indentation++;
            printLine("Assinatura (sem implementação)");
            indentation--;
        }
    }

    @Override
    public void visit(BlockRecord node) {
        printLine("Bloco");
        for (var command : node.commands()) {
            visitChild(command);
        }
    }

    @Override
    public void visit(DeclarationRecord node) {
        printLine("Declaração: " + node.type() + " " + node.name()
                + (node.value() == null ? " (sem inicialização)" : ""));
        if (node.value() != null) {
            visitChild(node.value());
        }
    }

    @Override
    public void visit(PrintRecord node) {
        printLine("Print");
        visitChild(node.expression());
    }

    @Override
    public void visit(WhileRecord node) {
        printLine("While");
        visitNamedChild("Condição", node.expression());
        visitChild(node.block());
    }

    @Override
    public void visit(IfRecord node) {
        printLine("If");
        visitNamedChild("Condição", node.condition());
        visitNamedChild("Então", node.thenBranch());
        if (node.elseBranch() != null) {
            visitNamedChild("Senão", node.elseBranch());
        }
    }

    @Override
    public void visit(ForRecord node) {
        printLine("For");
        if (node.initializer() != null) {
            visitNamedChild("Inicialização", node.initializer());
        }
        if (node.condition() != null) {
            visitNamedChild("Condição", node.condition());
        }
        if (node.update() != null) {
            visitNamedChild("Atualização", node.update());
        }
        visitChild(node.block());
    }

    @Override
    public void visit(BinaryExpressionRecord node) {
        printLine(node.operator().toString());
        visitChild(node.left());
        visitChild(node.right());
    }

    @Override
    public void visit(UnaryExpressionRecord node) {
        printLine(node.operator().toString());
        visitChild(node.operand());
    }

    @Override
    public void visit(LiteralRecord node) {
        Object value = node.value();
        if (value instanceof String text) {
            String escaped = text.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
            printLine("Literal: \"" + escaped + "\"");
        } else {
            printLine("Literal: " + value);
        }
    }

    @Override
    public void visit(MemberAccessRecord node) {
        printLine("Acesso a membro: " + node.name());
        visitChild(node.receiver());
    }

    @Override
    public void visit(MemberCallRecord node) {
        printLine("Chamada de método: " + node.name());
        visitChild(node.receiver());
        for (ExpressionInterface argument : node.arguments()) {
            visitChild(argument);
        }
    }

    @Override
    public void visit(NewObjectRecord node) {
        printLine("Instanciar: " + node.type().name());
        for (ExpressionInterface argument : node.arguments()) {
            visitChild(argument);
        }
    }

    @Override
    public void visit(VariableRecord node) {
        printLine("Variável: " + node.name());
    }

    @Override
    public void visit(RegisterRecord node) {
        printLine("Registrador: " + node.name());
    }

    @Override
    public void visit(RegisterDeclarationRecord node) {
        printLine("Inicialização/atribuição global: " + node.register().name());
        visitChild(node.value());
    }

    @Override
    public void visit(ParameterRecord node) {
        printLine("Parâmetro: " + node.type() + " " + node.name());
    }

    @Override
    public void visit(ReturnRecord node) {
        printLine("Return");
        if (node.value() != null) {
            visitChild(node.value());
        }
    }

    @Override
    public void visit(CallRecord node) {
        printLine("Chamada: " + node.name()
                + (node.arguments().isEmpty() ? " (sem argumentos)" : ""));
        for (var argument : node.arguments()) {
            visitChild(argument);
        }
    }

    @Override
    public void visit(ExpressionStatementRecord node) {
        printLine("Comando de expressão");
        visitChild(node.expression());
    }

    @Override
    public void visit(AttributeRecord node) {
        String visibility = node.visibility() == null
                ? " [PACKAGE]"
                : " [" + node.visibility() + "]";
        printLine("Atributo: " + node.type().name() + " " + node.name() + visibility
                + (node.isStatic() ? " [STATIC]" : "")
                + (node.isFinal() ? " [FINAL]" : "")
                + (node.value() == null ? " (sem inicialização)" : ""));
        if (node.value() != null) {
            visitChild(node.value());
        }
    }

    @Override
    public void visit(InterfaceRecord node) {
        printLine("Interface: " + node.name());

        indentation++;

        printLine("Funções:");

        indentation++;

        for (FunctionRecord function : node.functions()) {
            function.accept(this);
        }

        indentation -= 2;
    }

    @Override
    public void visit(SuperAttributeRecord node) {
        printLine("SuperAttribute: super." + node.name());
    }

    @Override
    public void visit(SuperCallRecord node) {
        printLine("SuperCall: super." + node.name());

        for (ExpressionInterface argument : node.arguments()) {
            argument.accept(this);
        }
    }

    @Override
    public void visit(ThisRecord record) {
        printLine("This");
    }

    @Override
    public void visit(ThisAttributeRecord record) {
        printLine("ThisAttribute: " + record.name());
    }

    @Override
    public void visit(ThisCallRecord record) {
        printLine("ThisCall: this." + record.name());
        for (ExpressionInterface argument : record.arguments()) {
            visitChild(argument);
        }
    }

    @Override
    public void visit(AssignmentRecord record) {
        printLine("Assignment");

        indentation++;
        record.target().accept(this);
        record.value().accept(this);
        indentation--;
    }

    @Override
    public void visit(ArrayAccessRecord node) {
        printLine("Acesso a array");
        visitNamedChild("Array", node.array());
        visitNamedChild("Índice", node.index());
    }

    @Override
    public void visit(ArrayCreationRecord node) {
        printLine("Criação de array: " + node.type().name());
        for (ExpressionInterface dimension : node.dimensions()) {
            visitNamedChild("Dimensão", dimension);
        }
        if (node.initializer() != null) {
            visitNamedChild("Inicializador", node.initializer());
        }
    }

    @Override
    public void visit(ArrayInitializerRecord node) {
        printLine("Inicializador de array");
        for (ExpressionInterface element : node.elements()) {
            visitChild(element);
        }
    }
}
