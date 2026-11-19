package compiler.ast.visitor;

import compiler.ast.ASTNode;
import compiler.ast.ExpressionInterface;
import compiler.ast.records.ArrayAccessRecord;
import compiler.ast.records.ArrayCreationRecord;
import compiler.ast.records.ArrayInitializerRecord;
import compiler.ast.records.AssignmentRecord;
import compiler.ast.records.AttributeRecord;
import compiler.ast.records.BinaryExpressionRecord;
import compiler.ast.records.BlockRecord;
import compiler.ast.records.CallRecord;
import compiler.ast.records.ClassRecord;
import compiler.ast.records.ConstructorRecord;
import compiler.ast.records.DeclarationRecord;
import compiler.ast.records.ExpressionStatementRecord;
import compiler.ast.records.ForRecord;
import compiler.ast.records.FreeRecord;
import compiler.ast.records.FunctionRecord;
import compiler.ast.records.IfRecord;
import compiler.ast.records.InterfaceRecord;
import compiler.ast.records.LiteralRecord;
import compiler.ast.records.MemberAccessRecord;
import compiler.ast.records.MemberCallRecord;
import compiler.ast.records.NewObjectRecord;
import compiler.ast.records.ParameterRecord;
import compiler.ast.records.PrintRecord;
import compiler.ast.records.PrintfRecord;
import compiler.ast.records.ProgramRecord;
import compiler.ast.records.RegisterDeclarationRecord;
import compiler.ast.records.RegisterRecord;
import compiler.ast.records.ReturnRecord;
import compiler.ast.records.SuperAttributeRecord;
import compiler.ast.records.SuperCallRecord;
import compiler.ast.records.ThisAttributeRecord;
import compiler.ast.records.ThisCallRecord;
import compiler.ast.records.ThisRecord;
import compiler.ast.records.UnaryExpressionRecord;
import compiler.ast.records.VariableRecord;
import compiler.ast.records.WhileRecord;

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
                + (node.isAbstract() ? " [ABSTRACT]" : "")
                + (node.isNative() ? " [NATIVE]" : "")
                + (node.isDefault() ? " [DEFAULT]" : ""));

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
        printLine(node.lineBreak() ? "PrintLn" : "Print");
        visitChild(node.expression());
    }

    @Override
    public void visit(PrintfRecord node) {
        printLine(node.lineBreak() ? "PrintfLn" : "Printf");
        visitNamedChild("Formato", node.format());
        for (ExpressionInterface argument : node.arguments()) {
            visitNamedChild("Argumento", argument);
        }
    }

    @Override
    public void visit(FreeRecord node) {
        printLine("Liberar memória:");
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
    public void visit(compiler.ast.records.ThrowRecord node) {
        printLine("Throw");
        visitChild(node.expression());
    }

    @Override
    public void visit(compiler.ast.records.TryRecord node) {
        printLine("Try");
        visitChild(node.tryBlock());
        for (compiler.ast.records.CatchRecord handler : node.catches()) {
            printLine("Catch " + handler.type().name() + " " + handler.name());
            visitChild(handler.block());
        }
        if (node.finallyBlock() != null) {
            printLine("Finally");
            visitChild(node.finallyBlock());
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
        printLine("Interface: " + node.name()
                + (node.superInterfaces().isEmpty()
                        ? ""
                        : " extends " + String.join(", ", node.superInterfaces())));

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
