package src;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import src.exceptions.LexicalException;
import src.exceptions.SyntacticException;
import src.records.ProgramRecord;

public class Main {

    public static void main(String[] args) throws Exception {
        String fileName = args.length > 0 ? args[0] : "./src/source/source.jvs";

        if (!fileName.endsWith(".jvs")) {
            System.err.println("Arquivo deve ser do tipo .jvs");
            return;
        }

        List<Token> tokens = new ArrayList<Token>();

        BufferedReader reader = new BufferedReader(new FileReader(fileName));
        String line;
        StringBuilder source = new StringBuilder();

        while ((line = reader.readLine()) != null) {
            source.append(line);
            source.append("\n");
        }

        reader.close();

        Lexer lexer = new Lexer(source.toString());

        try {
            tokens = lexer.analyze();
            Parser parser = new Parser(tokens);
            ProgramRecord program = parser.analyze();

            System.out.println("========== TOKENS ==========");
            for (Token token : tokens) {
                System.out.println(token);
            }
            System.out.println();

            System.out.println("========== PROGRAM ==========");
            System.out.println(program);
            System.out.println();

            System.out.println("========== Resultado ==========");
            System.out.println("Declaracao válida!");
        } catch (LexicalException e) {
            System.err.println(e.getMessage());
        } catch (SyntacticException e) {
            System.err.println(e.getMessage());
        }
    }
}
