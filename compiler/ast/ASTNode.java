package compiler.ast;

import compiler.ast.visitor.ASTVisitor;

public interface ASTNode {
    void accept(ASTVisitor visitor);
}
