package src.visitors;

import src.interfaces.visitor.ASTNode;
import src.interfaces.visitor.ASTVisitor;
import src.records.BinariaRecord;
import src.records.BlockRecord;
import src.records.CallRecord;
import src.records.ClassRecord;
import src.records.DeclarationRecord;
import src.records.ExpressionStatementRecord;
import src.records.FunctionRecord;
import src.records.JERecord;
import src.records.JMPRecord;
import src.records.LabelRecord;
import src.records.LiteralRecord;
import src.records.ParameterRecord;
import src.records.PopRecord;
import src.records.PrintRecord;
import src.records.ProgramRecord;
import src.records.PushRecord;
import src.records.RegisterRecord;
import src.records.ReturnRecord;
import src.records.VariavelRecord;
import src.records.WhileRecord;

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

        for (ClassRecord classe : node.classes()) {
            visitChild(classe);
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
    public void visit(BinariaRecord node) {
        printLine(node.operador().toString());
        visitChild(node.left());
        visitChild(node.right());
    }

    @Override
    public void visit(LiteralRecord node) {
        Object value = node.valor();
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
    public void visit(VariavelRecord node) {
        printLine("Variável: " + node.nome());
    }

    @Override
    public void visit(RegisterRecord node) {
        printLine("Registrador: " + node.nome());
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
        printLine("JMP → " + node.name());
    }

    @Override
    public void visit(JERecord node) {
        printLine("JE → " + node.target());
    }
}
