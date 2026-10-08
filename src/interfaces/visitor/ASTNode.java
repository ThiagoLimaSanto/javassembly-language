package src.interfaces.visitor;

public interface ASTNode {
    void accept(ASTVisitor visitor);
}
