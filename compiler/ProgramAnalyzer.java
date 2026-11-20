package compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
    public Path build(Path sourceFile) throws IOException {
        Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        analyze(normalizedSource);
        Path outputDirectory = findProjectDirectory(normalizedSource).resolve("out");
        String baseName = baseName(sourceFile);
        Path assembly = outputDirectory.resolve(baseName + ".asm");
        Path object = outputDirectory.resolve(baseName + ".o");
        Path executable = outputDirectory.resolve(baseName);

        runTool(List.of("nasm", "-f", "elf64", assembly.toString(), "-o", object.toString()),
                executable, "NASM");
        runTool(List.of("ld", "-o", executable.toString(), object.toString()),
                executable, "ld");
        return executable;
    }

    public int run(Path sourceFile) throws IOException, InterruptedException {
        Path executable = build(sourceFile);
        Process process = new ProcessBuilder(executable.toAbsolutePath().toString())
                .inheritIO()
                .start();
        return process.waitFor();
    }

    public void analyze(Path sourceFile) throws IOException {
        ProgramRecord program = Frontend.loadProject(sourceFile);
        String asm = analyzeProgram(program);

        String baseName = baseName(sourceFile);
        Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        Path projectDirectory = findProjectDirectory(normalizedSource);
        Path outputDirectory = projectDirectory.resolve("out");
        Path asmFile = outputDirectory.resolve(baseName + ".asm");
        try {
            Files.createDirectories(outputDirectory);
            Files.writeString(asmFile, asm);
        } catch (IOException cause) {
            throw new OutputException("Não foi possível gravar o assembly gerado.", asmFile, cause);
        }
    }

    private String baseName(Path sourceFile) {
        String fileName = sourceFile.getFileName().toString();
        int extension = fileName.lastIndexOf('.');
        return extension < 0 ? fileName : fileName.substring(0, extension);
    }

    private void runTool(List<String> command, Path outputPath, String tool) throws IOException {
        Process process;
        try {
            process = new ProcessBuilder(command).inheritIO().start();
        } catch (IOException cause) {
            throw new OutputException("Não foi possível iniciar '" + tool
                    + "'. Verifique se a ferramenta está instalada.", outputPath, cause);
        }
        try {
            int status = process.waitFor();
            if (status != 0) {
                throw new OutputException("'" + tool + "' terminou com código " + status + ".",
                        outputPath, null);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("A compilação foi interrompida.", interrupted);
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
        // Standalone .jvs files use an out directory beside the source file.
        return sourceFile.toAbsolutePath().normalize().getParent();
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
