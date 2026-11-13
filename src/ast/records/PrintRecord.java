package src.ast.records;

import src.ast.ExpressionInterface;
import src.ast.StatementInterface;
import src.ast.visitor.ASTVisitor;

public record PrintRecord(ExpressionInterface expression, int line, int column) implements StatementInterface {

    public PrintRecord(ExpressionInterface expression) {
        this(expression, 1, 1);
    }

     @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
