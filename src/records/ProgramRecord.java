package src.records;

import java.util.List;

import src.interfaces.visitor.ASTNode;
import src.interfaces.visitor.ASTVisitor;

public record ProgramRecord(List<ClassRecord> classes) implements ASTNode {

     @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
