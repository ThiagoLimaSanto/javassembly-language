package src.ast.records;

import java.util.List;

import src.ast.ExpressionInterface;
import src.ast.StatementInterface;
import src.ast.visitor.ASTVisitor;

public record PrintRecord(
        ExpressionInterface expression,
        List<ExpressionInterface> formatArguments,
        int line,
        int column) implements StatementInterface {

    public PrintRecord {
        formatArguments = formatArguments == null ? List.of() : List.copyOf(formatArguments);
    }

    public PrintRecord(ExpressionInterface expression, int line, int column) {
        this(expression, List.of(), line, column);
    }

    public PrintRecord(ExpressionInterface expression) {
        this(expression, List.of(), 1, 1);
    }

     @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
