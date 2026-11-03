package src;

import src.lexer.Lexer;
import src.lexer.Token;
import src.parser.Parser;
import src.semantic.SemanticAnalyzer;

import java.util.List;

import src.ast.records.ProgramRecord;
import src.ast.visitor.ASTPrinter;

public class ProgramAnalyzer {
    public void analyze(String source) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.analyze();

        Parser parser = new Parser(tokens);
        ProgramRecord program = parser.analyze();

        SemanticAnalyzer semantic = new SemanticAnalyzer();
        semantic.visit(program);

        program.accept(new ASTPrinter());
    }
}
