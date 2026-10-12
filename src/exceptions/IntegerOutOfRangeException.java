package src.exceptions;

public class IntegerOutOfRangeException extends LanguageException {

    public IntegerOutOfRangeException(
            String lexeme,
            int line,
            int column
    ) {
        super(
            "numérico",
            "O literal " + lexeme
                + " está fora do intervalo de int de 32 bits"
                + " (-2147483648 a 2147483647)",
            line,
            column
        );
    }
}