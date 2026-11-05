package src;

import java.io.BufferedReader;
import java.io.FileReader;

import src.exceptions.LanguageException;

public class Main {

    public static void main(String[] args) throws Exception {
        String fileName = args.length > 0 ? args[0] : "./src/source/source10.jvs";

        if (!fileName.endsWith(".jvs")) {
            System.err.println("Tipo de arquivo não reconhecido");
            return;
        }

        BufferedReader reader = new BufferedReader(new FileReader(fileName));
        String line;
        StringBuilder source = new StringBuilder();

        while ((line = reader.readLine()) != null) {
            source.append(line);
            source.append("\n");
        }

        reader.close();

        try {
            ProgramAnalyzer analyzer = new ProgramAnalyzer();
            analyzer.analyze(source.toString());

            System.out.println("========== Resultado ==========");
            System.out.println("Programa compilado com sucesso!");
        } catch (LanguageException e) {
            System.err.println(
                    "[" + e.getClass().getSimpleName() + "] "
                            + e.getMessage());
        }
    }
}
