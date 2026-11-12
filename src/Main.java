package src;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import src.exceptions.LanguageException;

public class Main {

    public static void main(String[] args) throws IOException {
        List<Path> files;

        if (args.length > 0) {
            files = Arrays.stream(args)
                    .map(Path::of)
                    .toList();
        } else {
            Path sourceDirectory = Path.of("src", "source");

            try (Stream<Path> entries = Files.list(sourceDirectory)) {
                files = entries
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".jvs"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
            }
        }

        if (files.isEmpty()) {
            System.out.println("Nenhum arquivo .jvs encontrado em src/source.");
            return;
        }

        int successful = 0;
        int failed = 0;

        for (Path file : files) {
            System.out.println("\n========== Arquivo: " + file + " ==========");

            if (!file.getFileName().toString().endsWith(".jvs")) {
                System.err.println("Tipo de arquivo não reconhecido; esperado .jvs");
                failed++;
                continue;
            }

            try {
                ProgramAnalyzer analyzer = new ProgramAnalyzer();
                analyzer.analyze(Files.readString(file));
                System.out.println("Programa compilado com sucesso!");
                successful++;
            } catch (LanguageException e) {
                System.err.println(
                        "[" + e.getClass().getSimpleName() + "] "
                                + e.getMessage());
                failed++;
            } catch (IOException e) {
                System.err.println(
                        "Não foi possível ler '" + file + "': " + e.getMessage());
                failed++;
            }
        }

        System.out.println("\n========== Resumo ==========");
        System.out.println("Compilados: " + successful);
        System.out.println("Com erros: " + failed);
    }
}
