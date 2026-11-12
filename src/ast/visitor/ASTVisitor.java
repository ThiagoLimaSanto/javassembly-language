package src.ast.visitor;

import src.ast.records.AssignmentRecord;
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
import src.ast.records.MemberAccessRecord;
import src.ast.records.MemberCallRecord;
import src.ast.records.ParameterRecord;
import src.ast.records.PopRecord;
import src.ast.records.PrintRecord;
import src.ast.records.ProgramRecord;
import src.ast.records.PushRecord;
import src.ast.records.RegisterRecord;
import src.ast.records.RegisterDeclarationRecord;
import src.ast.records.ReturnRecord;
import src.ast.records.SuperAttributeRecord;
import src.ast.records.SuperCallRecord;
import src.ast.records.ThisAttributeRecord;
import src.ast.records.ThisCallRecord;
import src.ast.records.ThisRecord;
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

    void visit(MemberAccessRecord node);

    void visit(MemberCallRecord node);

    void visit(VariableRecord node);

    void visit(RegisterRecord node);

    void visit(RegisterDeclarationRecord node);

    void visit(ParameterRecord node);

    void visit(ReturnRecord node);

    void visit(CallRecord node);

    void visit(ExpressionStatementRecord node);

    void visit(PushRecord node);

    void visit(PopRecord node);

    void visit(LabelRecord node);

    void visit(JMPRecord node);

    void visit(JERecord node);

    void visit(AttributeRecord node);

    void visit(InterfaceRecord node);

    void visit(SuperAttributeRecord node);

    void visit(SuperCallRecord node);

    void visit(ThisRecord node);

    void visit(ThisAttributeRecord node);

    void visit(ThisCallRecord node);

    void visit(AssignmentRecord node);
}
