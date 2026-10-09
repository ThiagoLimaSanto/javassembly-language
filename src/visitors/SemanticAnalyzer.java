package src.visitors;

import src.interfaces.StatementInterface;
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

public class SemanticAnalyzer implements ASTVisitor {

    @Override
    public void visit(ProgramRecord node) {
        for (ClassRecord classe : node.classes()) {
            classe.accept(this);
        }
    }

    @Override
    public void visit(ClassRecord node) {
        for (FunctionRecord funcao : node.functions()) {
            funcao.accept(this);
        }
    }

    @Override
    public void visit(FunctionRecord node) {
        node.block().accept(this);
    }

    @Override
    public void visit(BlockRecord node) {
        for (StatementInterface comando : node.commands()) {
            comando.accept(this);
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
    public void visit(BinariaRecord node) {
        node.left().accept(this);
        node.right().accept(this);
    }

    @Override
    public void visit(VariavelRecord node) {
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
