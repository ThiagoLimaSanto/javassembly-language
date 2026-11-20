package compiler;

import java.io.IOException;
import java.nio.file.Path;

import compiler.exceptions.CommandLineException;
import compiler.exceptions.LanguageException;
import compiler.exceptions.OutputException;

public class Main {
    private Main() {
    }

    public static void main(String[] args) {
        String command;
        String source;
        if (args.length == 2 && (args[0].equals("build") || args[0].equals("run"))) {
            command = args[0];
            source = args[1];
        } else {
            printError(new CommandLineException("Comando interno inválido. Use jvsc para compilar."));
            System.exit(2);
            return;
        }

        Path sourcePath = Path.of(source);
        if (!source.endsWith(".jvs")) {
            printError(new CommandLineException("Informe um arquivo-fonte com extensão .jvs."));
            System.exit(2);
            return;
        }

        try {
            ProgramAnalyzer analyzer = new ProgramAnalyzer();
            if (command.equals("build")) {
                Path executable = analyzer.build(sourcePath);
                System.out.println("Executável criado: " + executable.toAbsolutePath());
            } else {
                int status = analyzer.run(sourcePath);
                if (status != 0)
                    System.exit(status);
            }
        } catch (LanguageException exception) {
            printError(exception, exception.getSourcePath() == null
                    ? sourcePath
                    : exception.getSourcePath());
            System.exit(1);
        } catch (OutputException exception) {
            System.err.println("[OutputException] " + exception.getMessage());
            System.exit(1);
        } catch (IOException exception) {
            System.err.println("[IO] " + exception.getMessage());
            System.exit(1);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("[IO] A execução foi interrompida.");
            System.exit(130);
        }
    }

    private static void printError(LanguageException exception) {
        System.err.println("[" + exception.getClass().getSimpleName() + "] " + exception.getMessage());
    }

    private static void printError(LanguageException exception, Path sourcePath) {
        System.err.println(DiagnosticRenderer.render(sourcePath, exception));
    }

}
