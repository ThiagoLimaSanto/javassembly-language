package src.semantic;

import src.exceptions.SemanticException;

public class TypeChecker {

    public void validateType(
        SemanticType expected,
        SemanticType received,
        int line,
        int column
    ) {

        if (!expected.equals(received)) {

            throw new SemanticException(
                "Tipo incompativel. Esperado: " + expected +
                ", recebido: " + received,
                line,
                column
            );
        }
    }
}