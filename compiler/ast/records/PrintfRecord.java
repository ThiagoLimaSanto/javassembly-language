package compiler.ast.records;

import java.util.List;

import compiler.ast.ExpressionInterface;
import compiler.ast.StatementInterface;
import compiler.ast.visitor.ASTVisitor;

public record PrintfRecord(
        ExpressionInterface format,
        List<ExpressionInterface> arguments,
        boolean lineBreak,
        int line,
        int column) implements StatementInterface {

    public PrintfRecord {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    public PrintfRecord(
            ExpressionInterface format,
            List<ExpressionInterface> arguments,
            int line,
            int column) {
        this(format, arguments, true, line, column);
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
