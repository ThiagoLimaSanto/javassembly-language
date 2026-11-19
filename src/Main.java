package src;

import java.nio.file.Path;
import java.io.IOException;

import src.exceptions.LanguageException;
import src.exceptions.CommandLineException;
import src.exceptions.OutputException;

public class Main {

    public static void main(String[] args) throws Exception {
        String fileName = args.length > 0 ? args[0] : "./projects/exemplo/Teste.jvs";

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
