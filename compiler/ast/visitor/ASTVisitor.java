package compiler.ast.visitor;

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
import compiler.ast.records.ThrowRecord;
import compiler.ast.records.TryRecord;
import compiler.ast.records.UnaryExpressionRecord;
import compiler.ast.records.VariableRecord;
import compiler.ast.records.WhileRecord;

public interface ASTVisitor {

    void visit(ProgramRecord node);

    void visit(ClassRecord node);

    void visit(ConstructorRecord node);

    void visit(NewObjectRecord node);

    void visit(FunctionRecord node);

    void visit(BlockRecord node);

    void visit(DeclarationRecord node);

    void visit(PrintRecord node);

    void visit(PrintfRecord node);

    void visit(FreeRecord node);

    void visit(WhileRecord node);

    void visit(ForRecord node);

    void visit(IfRecord node);

    void visit(BinaryExpressionRecord node);

    void visit(UnaryExpressionRecord node);

    void visit(LiteralRecord node);

    void visit(MemberAccessRecord node);

    void visit(MemberCallRecord node);

    void visit(VariableRecord node);

    void visit(RegisterRecord node);

    void visit(RegisterDeclarationRecord node);

    void visit(ParameterRecord node);

    void visit(ReturnRecord node);

    void visit(ThrowRecord node);

    void visit(TryRecord node);

    void visit(CallRecord node);

    void visit(ExpressionStatementRecord node);

    void visit(AttributeRecord node);

    void visit(InterfaceRecord node);

    void visit(SuperAttributeRecord node);

    void visit(SuperCallRecord node);

    void visit(ThisRecord node);

    void visit(ThisAttributeRecord node);

    void visit(ThisCallRecord node);

    void visit(AssignmentRecord node);

    void visit(ArrayAccessRecord node);

    void visit(ArrayCreationRecord node);

    void visit(ArrayInitializerRecord node);
}
