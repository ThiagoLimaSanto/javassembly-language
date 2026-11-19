package compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import compiler.ast.records.ProgramRecord;
import compiler.backend.asm.NasmGenerator;
import compiler.backend.ir.IRGenerator;
import compiler.backend.memory.ObjectLayout;
import compiler.backend.optimization.IROptimizer;
import compiler.exceptions.OutputException;
import compiler.exceptions.SemanticException;
import compiler.semantic.EntryPointValidator;
import compiler.semantic.SemanticAnalyzer;

public class ProgramAnalyzer {
    public void analyze(Path sourceFile) throws IOException {
        ProgramRecord program = Frontend.loadProject(sourceFile);
        String asm = analyzeProgram(program);

        String fileName = sourceFile.getFileName().toString();
        int extension = fileName.lastIndexOf('.');
        String baseName = extension < 0 ? fileName : fileName.substring(0, extension);
        Path projectDirectory = findProjectDirectory(sourceFile);
        if (projectDirectory == null) {
            throw new OutputException(
                    "O arquivo-fonte deve estar dentro de 'projects/nomeProjeto/src'.",
                    sourceFile, null);
        }
        Path outputDirectory = projectDirectory.resolve("out");
        Path asmFile = outputDirectory.resolve(baseName + ".asm");
        try {
            Files.createDirectories(outputDirectory);
            Files.writeString(asmFile, asm);
        } catch (IOException cause) {
            throw new OutputException("Não foi possível gravar o assembly gerado.", asmFile, cause);
        }
    }

    private Path findProjectDirectory(Path sourceFile) {
        Path directory = sourceFile.toAbsolutePath().normalize().getParent();
        while (directory != null) {
            Path directoryName = directory.getFileName();
            if (directoryName != null && directoryName.toString().equals("src")) {
                return directory.getParent();
            }
            directory = directory.getParent();
        }
        return null;
    }

    public void analyze(String source) {
        ProgramRecord program = Frontend.parse(source);
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
        EntryPointValidator.validate(program);
        var classLayouts = new ObjectLayout().calculate(program);

        var ir = new IRGenerator(classLayouts).generate(program);
        ir = new IROptimizer().optimize(ir);
        return new NasmGenerator(program).generate(ir);
    }
}
