package src.ast;

public interface StatementInterface extends ASTNode {
    default int line() {
        return 1;
    }

    default int column() {
        return 1;
    }
}
