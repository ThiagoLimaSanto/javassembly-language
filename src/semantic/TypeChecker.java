package src.semantic;

import src.exceptions.SemanticException;
import src.lexer.TokenType;

public class TypeChecker {

    public void validateType(
            TokenType expected,
            TokenType received,
            int line,
            int column) {
        if (expected != received) {
            throw new SemanticException(
                    "Tipo incompativel. Esperado: " + expected +
                            ", recebido: " + received,
                    line,
                    column);
        }
    }
}