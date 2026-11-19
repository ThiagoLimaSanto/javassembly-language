package test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import src.Frontend;
import src.ast.records.ProgramRecord;
import src.backend.asm.NasmGenerator;
import src.backend.ir.IRGenerator;
import src.backend.ir.IRProgram;
import src.backend.memory.ObjectLayout;
import src.backend.optimization.IROptimizer;
import src.semantic.SemanticAnalyzer;

public final class BackendFixtureTests {
    private BackendFixtureTests() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Path fixtureRoot = args.length == 0
                ? Path.of("test/fixtures/backend")
                : Path.of(args[0]);
        if (!Files.isDirectory(fixtureRoot)) {
            throw new IllegalArgumentException("Backend fixture directory not found: " + fixtureRoot);
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
            throw new IllegalStateException("No backend .jvs fixtures found in " + fixtureRoot);
        }

        List<String> failures = new ArrayList<>();
        int passed = 0;
        for (Path fixture : fixtures) {
            String failure = runFixture(fixture);
            if (failure == null) {
                passed++;
                System.out.println("PASS " + fixture);
            } else {
                failures.add(failure);
                System.out.println("FAIL " + fixture + " - " + failure);
            }
        }

        System.out.println();
        System.out.println("Backend fixtures: " + passed + "/" + fixtures.size() + " passed.");
        if (!failures.isEmpty()) {
            throw new AssertionError(failures.size() + " backend fixture(s) failed.");
        }
    }

    private static String runFixture(Path fixture) {
        String fileName = fixture.getFileName().toString();
        boolean shouldRunSuccessfully = fileName.startsWith("accept_");
        boolean shouldFailAtRuntime = fileName.startsWith("reject_");
        if (!shouldRunSuccessfully && !shouldFailAtRuntime) {
            return "filename must start with accept_ or reject_";
        }

        String expectedSuffix = shouldRunSuccessfully ? ".out" : ".err";
        Path expectedFile = replaceExtension(fixture, expectedSuffix);
        if (!Files.isRegularFile(expectedFile)) {
            return "missing expected output file: " + expectedFile;
        }

        Path tempDirectory = null;
        try {
            tempDirectory = Files.createTempDirectory("javassembly-backend-");
            ProgramRecord program = Frontend.loadProject(fixture);
            new SemanticAnalyzer().visit(program);
            IRProgram ir = new IRGenerator(new ObjectLayout().calculate(program)).generate(program);
            ir = new IROptimizer().optimize(ir);

            Path assembly = tempDirectory.resolve("fixture.asm");
            Path objectFile = tempDirectory.resolve("fixture.o");
            Path executable = tempDirectory.resolve("fixture");
            Path stdoutFile = tempDirectory.resolve("stdout.txt");
            Path stderrFile = tempDirectory.resolve("stderr.txt");
            Files.writeString(assembly, new NasmGenerator(program).generate(ir));

            ProcessResult assembled = run(List.of(
                    "nasm", "-f", "elf64", assembly.toString(), "-o", objectFile.toString()), tempDirectory);
            if (assembled.exitCode() != 0) {
                return "nasm failed (" + assembled.exitCode() + "): " + assembled.stderr();
            }
            ProcessResult linked = run(List.of("ld", "-o", executable.toString(), objectFile.toString()),
                    tempDirectory);
            if (linked.exitCode() != 0) {
                return "ld failed (" + linked.exitCode() + "): " + linked.stderr();
            }

            Process process = new ProcessBuilder(executable.toString())
                    .directory(tempDirectory.toFile())
                    .redirectOutput(stdoutFile.toFile())
                    .redirectError(stderrFile.toFile())
                    .start();
            int exitCode = process.waitFor();
            String stdout = Files.readString(stdoutFile);
            String stderr = Files.readString(stderrFile);
            String expected = Files.readString(expectedFile);

            if (shouldRunSuccessfully) {
                if (exitCode != 0) {
                    return "expected exit code 0, got " + exitCode + "; stderr: " + stderr;
                }
                if (!stdout.equals(expected)) {
                    return "stdout mismatch; expected <" + expected + ">, got <" + stdout + ">";
                }
                if (!stderr.isEmpty()) {
                    return "expected empty stderr, got <" + stderr + ">";
                }
            } else {
                if (exitCode == 0) {
                    return "expected a runtime failure, but program exited successfully";
                }
                if (!stdout.isEmpty()) {
                    return "expected empty stdout before runtime failure, got <" + stdout + ">";
                }
                if (!stderr.equals(expected)) {
                    return "stderr mismatch; expected <" + expected + ">, got <" + stderr + ">";
                }
            }
            return null;
        } catch (IOException | InterruptedException | RuntimeException exception) {
            return exception.getClass().getSimpleName() + ": " + exception.getMessage();
        } finally {
            if (tempDirectory != null) {
                deleteRecursively(tempDirectory);
            }
        }
    }

    private static ProcessResult run(List<String> command, Path directory)
            throws IOException, InterruptedException {
        Path stdoutFile = Files.createTempFile(directory, "process-out-", ".txt");
        Path stderrFile = Files.createTempFile(directory, "process-err-", ".txt");
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectOutput(stdoutFile.toFile())
                .redirectError(stderrFile.toFile())
                .start();
        int exitCode = process.waitFor();
        String stdout = Files.readString(stdoutFile);
        String stderr = Files.readString(stderrFile);
        Files.deleteIfExists(stdoutFile);
        Files.deleteIfExists(stderrFile);
        return new ProcessResult(exitCode, stdout, stderr);
    }

    private static Path replaceExtension(Path path, String extension) {
        String fileName = path.getFileName().toString();
        return path.resolveSibling(fileName.substring(0, fileName.length() - 4) + extension);
    }

    private static void deleteRecursively(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}
