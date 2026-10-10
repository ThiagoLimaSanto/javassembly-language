package src.ast.visitor;

import src.ast.ASTNode;
import src.ast.visitor.ASTVisitor;
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

        for (ClassRecord classNode : node.classes()) {
            visitChild(classNode);
        }
    }

    @Override
    public void visit(ClassRecord node) {
        String visibility = node.visibility() == null
                ? ""
                : " [" + node.visibility() + "]";

        printLine("Classe " + node.name() + visibility);

        for (FunctionRecord function : node.functions()) {
            visitChild(function);
        }
    }

    @Override
    public void visit(FunctionRecord node) {
        String visibility = node.visibility() == null
                ? "" : " [" + node.visibility() + "]";
        printLine("Função " + node.name() + " → " + node.returnType() + visibility);
        for (ParameterRecord parameter : node.parameters()) {
            visitChild(parameter);
        }
        visitChild(node.block());
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
    public void visit(BinaryExpressionRecord node) {
        printLine(node.operator().toString());
        visitChild(node.left());
        visitChild(node.right());
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
    public void visit(VariableRecord node) {
        printLine("Variável: " + node.name());
    }

    @Override
    public void visit(RegisterRecord node) {
        printLine("Registrador: " + node.name());
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
    public void visit(PushRecord node) {
        printLine("Push");
        visitChild(node.register());
    }

    @Override
    public void visit(PopRecord node) {
        printLine("Pop");
        visitChild(node.register());
    }

    @Override
    public void visit(LabelRecord node) {
        printLine("Label: " + node.name());
    }

    @Override
    public void visit(JMPRecord node) {
        printLine("JMP → " + node.target());
    }

    @Override
    public void visit(JERecord node) {
        printLine("JE → " + node.target());
    }
}
