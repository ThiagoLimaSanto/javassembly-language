package src.interfaces.visitor;

import src.records.BinariaRecord;
import src.records.BlockRecord;
import src.records.ClassRecord;
import src.records.DeclarationRecord;
import src.records.FunctionRecord;
import src.records.LiteralRecord;
import src.records.PrintRecord;
import src.records.ProgramRecord;
import src.records.RegistradorRecord;
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
}
