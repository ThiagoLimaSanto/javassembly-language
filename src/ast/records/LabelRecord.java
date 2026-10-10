package src.ast.records;

import src.ast.StatementInterface;
import src.ast.visitor.ASTVisitor;

public record LabelRecord(String name) implements StatementInterface {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }

}
