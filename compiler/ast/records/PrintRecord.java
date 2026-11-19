package compiler.ast.records;

import compiler.ast.ExpressionInterface;
import compiler.ast.StatementInterface;
import compiler.ast.visitor.ASTVisitor;

public record PrintRecord(
        ExpressionInterface expression,
        boolean lineBreak,
        int line,
        int column) implements StatementInterface {

    public PrintRecord(ExpressionInterface expression) {
        this(expression, true, 1, 1);
    }

    public PrintRecord(ExpressionInterface expression, int line, int column) {
        this(expression, true, line, column);
    }

     @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
