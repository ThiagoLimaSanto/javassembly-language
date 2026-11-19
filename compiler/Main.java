package compiler;

import java.nio.file.Path;

import compiler.exceptions.CommandLineException;
import compiler.exceptions.LanguageException;
import compiler.exceptions.OutputException;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws Exception {
        String fileName = args.length > 0 ? args[0] : "./projects/exemplo/src/Teste.jvs";

        try {
            if (!fileName.endsWith(".jvs")) {
                throw new CommandLineException(
                        "Tipo de arquivo não reconhecido. Informe um arquivo com extensão .jvs.");
            }
            if (args.length > 1) {
                throw new CommandLineException("Informe somente um arquivo-fonte por execução.");
            }
            ProgramAnalyzer analyzer = new ProgramAnalyzer();
            analyzer.analyze(Path.of(fileName));
        } catch (LanguageException e) {
            System.err.println(
                    "[" + e.getClass().getSimpleName() + "] "
                            + e.getMessage());
            System.exit(1);
        } catch (OutputException e) {
            System.err.println("[OutputException] " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("[IO] " + e.getMessage());
            System.exit(1);
        }
    }
}
