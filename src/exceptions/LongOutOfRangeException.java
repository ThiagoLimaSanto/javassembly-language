package src.exceptions;

public class LongOutOfRangeException extends LanguageException {

    public LongOutOfRangeException(String lexeme, int line, int column) {
        super(
                "numérico",
                "O literal " + lexeme
                        + " está fora do intervalo de long de 64 bits"
                        + " (-9223372036854775808 a 9223372036854775807)",
                line,
                column);
    }
}
