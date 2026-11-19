package src;

import java.nio.file.Path;
import java.io.IOException;

import src.exceptions.LanguageException;

public class Main {

    public static void main(String[] args) throws Exception {
        String fileName = args.length > 0 ? args[0] : "./projects/exemplo/Teste.jvs";

        if (!fileName.endsWith(".jvs")) {
            System.err.println("Tipo de arquivo não reconhecido");
            System.exit(1);
        }

        try {
            ProgramAnalyzer analyzer = new ProgramAnalyzer();
            analyzer.analyze(Path.of(fileName));
        } catch (LanguageException e) {
            System.err.println(
                    "[" + e.getClass().getSimpleName() + "] "
                            + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("[IO] " + e.getMessage());
            System.exit(1);
        }
    }
}
