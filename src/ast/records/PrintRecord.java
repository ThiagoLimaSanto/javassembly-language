package src.ast.records;

import src.ast.ExpressionInterface;
import src.ast.StatementInterface;
import src.ast.visitor.ASTVisitor;

public record PrintRecord(ExpressionInterface expression) implements StatementInterface {

     @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
