package src.records;


import src.enums.TokenType;
import src.interfaces.ExpressionInterface;
import src.interfaces.visitor.ASTVisitor;

public record BinariaRecord(
        TokenType operador,
        ExpressionInterface left,
        ExpressionInterface right) implements ExpressionInterface {

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
