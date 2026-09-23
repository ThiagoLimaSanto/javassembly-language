import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import exceptions.LexicalException;

public class Main {

    public static void main(String[] args) throws Exception {
        String fileName = args.length > 0 ? args[0] : "source.jvs";

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

            for (Token token : tokens) {
                System.out.println(token);
            }
        } catch (LexicalException e) {
            System.err.println(e.getMessage());
        }
    }
}
