package src.ast;

import src.ast.visitor.ASTVisitor;

public interface ASTNode {
    void accept(ASTVisitor visitor);
}
