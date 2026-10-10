package src;

import src.lexer.Lexer;
import src.lexer.Token;
import src.parser.Parser;

import java.util.List;

import src.ast.records.ProgramRecord;

public class ProgramAnalyzer {
    public ProgramRecord analyze(String source) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.analyze();

        Parser parser = new Parser(tokens);
        return parser.analyze();
    }
}
