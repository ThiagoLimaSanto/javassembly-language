package src;

import java.nio.file.Path;
import java.io.IOException;

import src.exceptions.LanguageException;

public class Main {

    public static void main(String[] args) throws Exception {
        String fileName = args.length > 0 ? args[0] : "./src/source/rules/syntax/accept_control_flow_and_expressions.jvs";

        if (!fileName.endsWith(".jvs")) {
            System.err.println("Tipo de arquivo não reconhecido");
            return;
        }

        try {
            ProgramAnalyzer analyzer = new ProgramAnalyzer();
            analyzer.analyze(Path.of(fileName));
        } catch (LanguageException e) {
            System.err.println(
                    "[" + e.getClass().getSimpleName() + "] "
                            + e.getMessage());
        } catch (IOException e) {
            System.err.println("[IO] " + e.getMessage());
        }
    }
}
