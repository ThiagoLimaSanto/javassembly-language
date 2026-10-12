package src.exceptions;

public class DoubleOutOfRangeException extends LanguageException {

    public DoubleOutOfRangeException(
            String lexeme,
            int line,
            int column
    ) {
        super(
            "numérico",
            "O literal " + lexeme
                + " excede o maior valor finito de double"
                + " (" + Double.MAX_VALUE + ")",
            line,
            column
        );
    }
}