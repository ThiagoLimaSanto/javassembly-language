package src.ast.records;

import src.ast.ExpressionInterface;
import src.ast.StatementInterface;
import src.ast.visitor.ASTVisitor;

public record DeclarationRecord(
        TypeRecord type,
        String name,
        ExpressionInterface value,
        int line,
        int column
    ) implements StatementInterface {

     @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
