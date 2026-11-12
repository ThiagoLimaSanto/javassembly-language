package src;

import java.io.IOException;
import java.nio.file.Path;

import src.lexer.Lexer;
import src.lexer.Token;
import src.parser.Parser;
import src.semantic.SemanticAnalyzer;

import java.util.List;

import src.ast.records.ProgramRecord;
import src.ast.visitor.ASTPrinter;
import src.exceptions.SemanticException;

public class ProgramAnalyzer {
    public void analyze(Path sourceFile) throws IOException {
        ProgramRecord program = new SourceProjectLoader().load(sourceFile);
        analyzeProgram(program);
    }

    public void analyze(String source) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.analyze();

        Parser parser = new Parser(tokens);
        ProgramRecord program = parser.analyze();
        if (!program.imports().isEmpty()) {
            throw new SemanticException(
                    "A análise de arquivos com import precisa receber o caminho do arquivo-fonte.",
                    1,
                    1);
        }

        analyzeProgram(program);
    }

    private void analyzeProgram(ProgramRecord program) {
        SemanticAnalyzer semantic = new SemanticAnalyzer();
        semantic.visit(program);

        program.accept(new ASTPrinter());
    }
}
