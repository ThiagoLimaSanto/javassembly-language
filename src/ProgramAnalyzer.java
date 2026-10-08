package src;

import java.util.List;

import src.records.ProgramRecord;

public class ProgramAnalyzer {
    public ProgramRecord analyze(String source) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.analyze();

        Parser parser = new Parser(tokens);
        return parser.analyze();
    }
}
