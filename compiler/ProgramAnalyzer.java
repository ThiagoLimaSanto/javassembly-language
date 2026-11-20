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
        return build(sourceFile, TargetPlatform.LINUX_X64);
    }

    public Path build(Path sourceFile, TargetPlatform target) throws IOException {
        Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        analyze(normalizedSource, target);
        Path outputDirectory = outputDirectory(normalizedSource, target);
        String baseName = baseName(sourceFile);
        Path assembly = outputDirectory.resolve(baseName + ".asm");
        Path object = outputDirectory.resolve(baseName + target.objectExtension());
        Path executable = outputDirectory.resolve(baseName + target.executableExtension());

        runTool(List.of("nasm", "-f", target.nasmFormat(), assembly.toString(), "-o", object.toString()),
                executable, "NASM");
        runLinker(target, object, executable);
        return executable;
    }

    public int run(Path sourceFile) throws IOException, InterruptedException {
        return run(sourceFile, TargetPlatform.LINUX_X64);
    }

    public int run(Path sourceFile, TargetPlatform target) throws IOException, InterruptedException {
        build(sourceFile, target);
        return execute(sourceFile, target);
    }

    public int execute(Path sourceFile, TargetPlatform target) throws IOException, InterruptedException {
        Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        Path executable = outputDirectory(normalizedSource, target)
                .resolve(baseName(normalizedSource) + target.executableExtension());
        if (!Files.isRegularFile(executable)) {
            throw new OutputException("Executável não encontrado. Compile primeiro com jvsc.", executable, null);
        }
        boolean windowsHost = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).startsWith("windows");
        if (target.isWindows() && !windowsHost) {
            throw new OutputException(
                    "O executável Windows foi criado, mas só pode ser executado no Windows ou via Wine.",
                    executable, null);
        }
        if (!target.isWindows() && windowsHost) {
            throw new OutputException(
                    "O executável Linux foi criado, mas precisa ser executado em Linux ou WSL.",
                    executable, null);
        }
        Process process = new ProcessBuilder(executable.toAbsolutePath().toString())
                .inheritIO()
                .start();
        return process.waitFor();
    }

    public void analyze(Path sourceFile) throws IOException {
        analyze(sourceFile, TargetPlatform.LINUX_X64);
    }

    public void analyze(Path sourceFile, TargetPlatform target) throws IOException {
        ProgramRecord program = Frontend.loadProject(sourceFile);
        String asm = analyzeProgram(program, target);

        String baseName = baseName(sourceFile);
        Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        Path outputDirectory = outputDirectory(normalizedSource, target);
        Path asmFile = outputDirectory.resolve(baseName + ".asm");
        try {
            Files.createDirectories(outputDirectory);
            Files.writeString(asmFile, asm);
        } catch (IOException cause) {
            throw new OutputException("Não foi possível gravar o assembly gerado.", asmFile, cause);
        }
    }

    private Path outputDirectory(Path sourceFile, TargetPlatform target) {
        Path root = findProjectDirectory(sourceFile).resolve("out");
        return target == TargetPlatform.LINUX_X64
                ? root
                : root.resolve(target.directoryName());
    }

    private void runLinker(TargetPlatform target, Path object, Path executable) throws IOException {
        if (target == TargetPlatform.LINUX_X64) {
            runTool(List.of("ld", "-o", executable.toString(), object.toString()), executable, "ld");
            return;
        }

        boolean windowsHost = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).startsWith("windows");
        String configuredLinker = System.getenv("JAVASSEMBLY_WINDOWS_LINKER");
        String linker = configuredLinker == null || configuredLinker.isBlank()
                ? windowsHost ? "ld.exe" : "x86_64-w64-mingw32-ld"
                : configuredLinker;
        runTool(List.of(linker, "-e", "start", "--subsystem", "console", "-o",
                executable.toString(), object.toString(), "-lkernel32"), executable, linker);
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
        return analyzeProgram(program, TargetPlatform.LINUX_X64);
    }

    private String analyzeProgram(ProgramRecord program, TargetPlatform target) {
        SemanticAnalyzer semantic = new SemanticAnalyzer();
        semantic.visit(program);
        EntryPointValidator.validate(program);
        var classLayouts = new ObjectLayout().calculate(program);

        var ir = new IRGenerator(classLayouts).generate(program);
        ir = new IROptimizer().optimize(ir);
        return new NasmGenerator(program, target).generate(ir);
    }
}
