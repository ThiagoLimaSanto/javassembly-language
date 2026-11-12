package test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import src.ProgramAnalyzer;
import src.exceptions.LanguageException;
import src.exceptions.LexicalException;
import src.exceptions.SemanticException;
import src.exceptions.SyntacticException;

public final class RuleFixtureTests {
    private RuleFixtureTests() {
    }

    public static void main(String[] args) throws IOException {
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

    private static String runFixture(
            Path fixture,
            boolean shouldAccept,
            Class<? extends LanguageException> expectedException,
            PrintStream output,
            PrintStream suppressedOutput) {
        try {
            System.setOut(suppressedOutput);
            new ProgramAnalyzer().analyze(fixture);
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
