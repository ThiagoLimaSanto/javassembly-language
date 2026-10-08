package src.interfaces.visitor;

import src.records.BinariaRecord;
import src.records.BlockRecord;
import src.records.CallRecord;
import src.records.ClassRecord;
import src.records.DeclarationRecord;
import src.records.ExpressionStatementRecord;
import src.records.FunctionRecord;
import src.records.LiteralRecord;
import src.records.ParameterRecord;
import src.records.PrintRecord;
import src.records.ProgramRecord;
import src.records.RegistradorRecord;
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

    void visit(RegistradorRecord node);

    void visit(ParameterRecord node);

    void visit(ReturnRecord node);

    void visit(CallRecord node);

    void visit(ExpressionStatementRecord node);
}
