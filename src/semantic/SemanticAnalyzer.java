package src.semantic;

import src.ast.StatementInterface;
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

public class SemanticAnalyzer implements ASTVisitor {

    @Override
    public void visit(ProgramRecord node) {
        for (ClassRecord classNode : node.classes()) {
            classNode.accept(this);
        }
    }

    @Override
    public void visit(ClassRecord node) {
        for (FunctionRecord function : node.functions()) {
            function.accept(this);
        }
    }

    @Override
    public void visit(FunctionRecord node) {
        node.block().accept(this);
    }

    @Override
    public void visit(BlockRecord node) {
        for (StatementInterface statement : node.commands()) {
            statement.accept(this);
        }
    }

    @Override
    public void visit(DeclarationRecord node) {
        if (node.value() != null) {
            node.value().accept(this);
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
        // A verificação do uso da variável entrará aqui futuramente.
    }

    @Override
    public void visit(LiteralRecord node) {
        // Folha: não possui filhos para visitar.
    }

    @Override
    public void visit(RegisterRecord node) {
        // Folha: não possui filhos para visitar.
    }

    @Override
    public void visit(ParameterRecord node) {

    }

    @Override
    public void visit(ReturnRecord node) {
        if (node.value() != null) {
            node.value().accept(this);
        }
    }

    @Override
    public void visit(CallRecord node) {
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
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
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
    }

    @Override
    public void visit(JMPRecord node) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
    }

    @Override
    public void visit(JERecord node) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
    }
}
