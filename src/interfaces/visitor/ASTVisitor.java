package src.interfaces.visitor;

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

public interface ASTVisitor {
    void visit(ProgramRecord node);

    void visit(ClassRecord node);

    void visit(FunctionRecord node);

    void visit(BlockRecord node);

    void visit(DeclarationRecord node);

    void visit(PrintRecord node);

    void visit(WhileRecord node);

    void visit(BinariaRecord node);

    void visit(LiteralRecord node);

    void visit(VariavelRecord node);

    void visit(RegisterRecord node);

    void visit(ParameterRecord node);

    void visit(ReturnRecord node);

    void visit(CallRecord node);

    void visit(ExpressionStatementRecord node);

    void visit(PushRecord node);

    void visit(PopRecord node);

    void visit(LabelRecord node);

    void visit(JMPRecord node);

    void visit(JERecord node);
}
