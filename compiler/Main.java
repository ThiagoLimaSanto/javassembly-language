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
        TargetPlatform target;
        String source;
        String[] programArguments = new String[0];
        boolean knownCommand = args.length > 0
                && (args[0].equals("build") || args[0].equals("run") || args[0].equals("exec"));
        boolean hasTarget = args.length > 1
                && (args[1].equals("-l") || args[1].equals("-w"));
        if (knownCommand && hasTarget && args.length >= 3) {
            command = args[0];
            target = TargetPlatform.fromFlag(args[1]);
            source = args[2];
            if (command.equals("build") && args.length != 3) {
                printError(new CommandLineException("O comando build não recebe argumentos do programa."));
                System.exit(2);
                return;
            }
            programArguments = java.util.Arrays.copyOfRange(args, 3, args.length);
        } else if (knownCommand && args.length >= 2 && !hasTarget) {
            command = args[0];
            target = TargetPlatform.LINUX_X64;
            source = args[1];
            if (command.equals("build") && args.length != 2) {
                printError(new CommandLineException("O comando build não recebe argumentos do programa."));
                System.exit(2);
                return;
            }
            programArguments = java.util.Arrays.copyOfRange(args, 2, args.length);
        } else if (knownCommand && args.length >= 2) {
            printError(new CommandLineException("Destino inválido '" + args[1]
                    + "'. Use -l (Linux x86-64) ou -w (Windows x86-64)."));
            System.exit(2);
            return;
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
                Path executable = analyzer.build(sourcePath, target);
                System.out.println("Executável criado: " + executable.toAbsolutePath());
            } else if (command.equals("exec")) {
                int status = analyzer.execute(sourcePath, target, programArguments);
                if (status != 0) System.exit(status);
            } else {
                int status = analyzer.run(sourcePath, target, programArguments);
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
