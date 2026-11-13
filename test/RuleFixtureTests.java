package test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import src.Frontend;
import src.ast.records.ProgramRecord;
import src.exceptions.LanguageException;
import src.exceptions.LexicalException;
import src.exceptions.LongOutOfRangeException;
import src.exceptions.SemanticException;
import src.exceptions.SyntacticException;
import src.lexer.Lexer;
import src.lexer.Token;
import src.lexer.TokenType;
import src.parser.TokenStream;
import src.semantic.SemanticAnalyzer;

public final class RuleFixtureTests {
    private RuleFixtureTests() {
    }

    public static void main(String[] args) throws IOException {
        runFrontendCoreChecks();
        Path fixtureRoot = args.length == 0
                ? Path.of("src/source/rules")
                : Path.of(args[0]);

        if (!Files.isDirectory(fixtureRoot)) {
            throw new IllegalArgumentException(
                    "Fixture directory not found: " + fixtureRoot);
        }

        List<Path> fixtures;
        try (var paths = Files.walk(fixtureRoot)) {
            fixtures = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jvs"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }

        if (fixtures.isEmpty()) {
            throw new IllegalStateException(
                    "No .jvs rule fixtures found in " + fixtureRoot);
        }

        PrintStream output = System.out;
        PrintStream suppressedOutput = new PrintStream(OutputStream.nullOutputStream());
        List<String> failures = new ArrayList<>();
        int passed = 0;

        try {
            for (Path fixture : fixtures) {
                String name = fixture.getFileName().toString();
                boolean shouldAccept;
                if (name.startsWith("accept_")) {
                    shouldAccept = true;
                } else if (name.startsWith("reject_")) {
                    shouldAccept = false;
                } else {
                    failures.add(fixture + ": filename must start with accept_ or reject_");
                    output.println("FAIL " + fixture);
                    continue;
                }

                Class<? extends LanguageException> expectedException =
                        shouldAccept ? null : expectedException(fixture);
                String failure = runFixture(fixture, shouldAccept, expectedException, output,
                        suppressedOutput);

                if (failure == null) {
                    passed++;
                    output.println("PASS " + fixture);
                } else {
                    failures.add(failure);
                    output.println("FAIL " + fixture + " - " + failure);
                }
            }
        } finally {
            System.setOut(output);
            suppressedOutput.close();
        }

        output.println();
        output.println("Rule fixtures: " + passed + "/" + fixtures.size() + " passed.");
        if (!failures.isEmpty()) {
            throw new AssertionError(
                    failures.size() + " rule fixture(s) failed.");
        }
    }

    private static void runFrontendCoreChecks() {
        Lexer lexer = new Lexer("int value = 1;\r\n");
        List<Token> firstResult = lexer.analyze();
        List<Token> secondResult = lexer.analyze();
        if (firstResult != secondResult) {
            throw new AssertionError("Lexer should return its cached token list");
        }
        Token eof = firstResult.get(firstResult.size() - 1);
        if (eof.getType() != TokenType.EOF || eof.getLine() != 2 || eof.getColumn() != 1) {
            throw new AssertionError("Lexer returned an incorrect EOF position");
        }
        try {
            firstResult.add(new Token(TokenType.EOF, "EOF", 2, 1));
            throw new AssertionError("Lexer token list should be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected: token snapshots cannot be changed by downstream stages.
        }

        TokenStream stream = new TokenStream(firstResult);
        if (stream.peek(100).getType() != TokenType.EOF) {
            throw new AssertionError("Peeking beyond the token stream should return EOF");
        }
        stream.consume(TokenType.INT, "Expected test token");
        if (stream.peek(Integer.MAX_VALUE).getType() != TokenType.EOF) {
            throw new AssertionError("Large peek offsets should return EOF");
        }
        expectIllegalArgument(() -> stream.peek(-1), "negative token offset");
        expectIllegalArgument(
                () -> new TokenStream(List.of(
                        new Token(TokenType.EOF, "EOF", 1, 1),
                        new Token(TokenType.IDENTIFIER, "afterEof", 1, 1),
                        new Token(TokenType.EOF, "EOF", 1, 1))),
                "EOF before the end of the token stream");

        String unreachableSource = "public class UnreachablePosition {\n"
                + "    public static func void main() {\n"
                + "        return;\n"
                + "        print 1;\n"
                + "    }\n"
                + "}";
        try {
            ProgramRecord program = Frontend.parse(unreachableSource);
            new SemanticAnalyzer().visit(program);
            throw new AssertionError("Unreachable statement should be rejected");
        } catch (SemanticException exception) {
            if (exception.getLine() != 4 || exception.getColumn() != 9) {
                throw new AssertionError(
                        "Unreachable statement should report line 4, column 9; got line "
                                + exception.getLine() + ", column " + exception.getColumn());
            }
        }

        String positiveOverflow = "public class PositiveOverflow {\n"
                + "    public static func void main() {\n"
                + "        int value = 2147483648;\n"
                + "        return;\n"
                + "    }\n"
                + "}";
        try {
            ProgramRecord program = Frontend.parse(positiveOverflow);
            new SemanticAnalyzer().visit(program);
            throw new AssertionError("A long literal should not be assignable to int");
        } catch (SemanticException expected) {
            // 2147483648 is now a valid long literal, but cannot narrow to int.
        }

        String longOverflow = "public class LongOverflow {\n"
                + "    public static func void main() {\n"
                + "        long value = 9223372036854775808L;\n"
                + "        return;\n"
                + "    }\n"
                + "}";
        try {
            Frontend.parse(longOverflow);
            throw new AssertionError("Positive value beyond Long.MAX_VALUE should fail");
        } catch (LongOutOfRangeException expected) {
            // The direct positive literal is outside the signed 64-bit range.
        }
    }

    private static void expectIllegalArgument(Runnable action, String description) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException for " + description);
    }

    private static String runFixture(
            Path fixture,
            boolean shouldAccept,
            Class<? extends LanguageException> expectedException,
            PrintStream output,
            PrintStream suppressedOutput) {
        try {
            System.setOut(suppressedOutput);
            ProgramRecord program = Frontend.loadProject(fixture);
            new SemanticAnalyzer().visit(program);
            return shouldAccept
                    ? null
                    : "expected " + expectedException.getSimpleName() + " but was accepted";
        } catch (LanguageException exception) {
            if (shouldAccept) {
                return "unexpected " + exception.getClass().getSimpleName()
                        + ": " + exception.getMessage();
            }
            if (expectedException.isInstance(exception)) {
                return null;
            }
            return "expected " + expectedException.getSimpleName()
                    + " but got " + exception.getClass().getSimpleName()
                    + ": " + exception.getMessage();
        } catch (IOException | RuntimeException exception) {
            return "unexpected " + exception.getClass().getSimpleName()
                    + ": " + exception.getMessage();
        } finally {
            System.setOut(output);
        }
    }

    private static Class<? extends LanguageException> expectedException(Path fixture) {
        for (Path segment : fixture) {
            switch (segment.toString()) {
                case "lexical":
                    return LexicalException.class;
                case "syntax":
                    return SyntacticException.class;
                case "semantic":
                    return SemanticException.class;
                default:
                    break;
            }
        }
        throw new IllegalArgumentException(
                "Reject fixture must be inside lexical, syntax, or semantic: " + fixture);
    }
}
