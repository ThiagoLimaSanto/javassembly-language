package src;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import src.ast.records.ProgramRecord;
import src.backend.asm.NasmGenerator;
import src.backend.ir.IRGenerator;
import src.exceptions.SemanticException;
import src.lexer.Lexer;
import src.lexer.Token;
import src.parser.Parser;
import src.semantic.SemanticAnalyzer;

public class ProgramAnalyzer {
    public void analyze(Path sourceFile) throws IOException {
        ProgramRecord program = new SourceProjectLoader().load(sourceFile);
        String asm = analyzeProgram(program);

        String fileName = sourceFile.getFileName().toString();
        int extension = fileName.lastIndexOf('.');
        String baseName = extension < 0 ? fileName : fileName.substring(0, extension);
        String asmName = baseName + ".asm";
        Path asmFile = sourceFile.resolveSibling(asmName);

        Files.writeString(asmFile, asm);
        System.out.println("Assembly salvo em: " + asmFile);
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

        System.out.println(analyzeProgram(program));
    }

    private String analyzeProgram(ProgramRecord program) {
        SemanticAnalyzer semantic = new SemanticAnalyzer();
        semantic.visit(program);

        var ir = new IRGenerator().generate(program);
        return new NasmGenerator().generate(ir);
    }
}
