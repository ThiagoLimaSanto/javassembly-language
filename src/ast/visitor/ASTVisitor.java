package src.ast.visitor;

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

public interface ASTVisitor {
    void visit(ProgramRecord node);

    void visit(ClassRecord node);

    void visit(FunctionRecord node);

    void visit(BlockRecord node);

    void visit(DeclarationRecord node);

    void visit(PrintRecord node);

    void visit(WhileRecord node);

    void visit(BinaryExpressionRecord node);

    void visit(LiteralRecord node);

    void visit(VariableRecord node);

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
