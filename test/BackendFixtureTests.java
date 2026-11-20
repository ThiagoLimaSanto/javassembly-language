package test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import compiler.Frontend;
import compiler.TargetPlatform;
import compiler.WindowsLinker;
import compiler.ast.records.ProgramRecord;
import compiler.backend.asm.NasmGenerator;
import compiler.backend.ir.IRGenerator;
import compiler.backend.ir.IRProgram;
import compiler.backend.memory.ObjectLayout;
import compiler.backend.optimization.IROptimizer;
import compiler.semantic.SemanticAnalyzer;

public final class BackendFixtureTests {
    private BackendFixtureTests() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Path fixtureRoot = args.length == 0
                ? Path.of("test/fixtures/backend")
                : Path.of(args[0]);
        TargetPlatform target = args.length < 2
                ? TargetPlatform.LINUX_X64
                : TargetPlatform.fromFlag(args[1]);
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
        int skipped = 0;
        for (Path fixture : fixtures) {
            if (target.isWindows()
                    && Files.isRegularFile(replaceExtension(fixture, ".low_data_limit"))) {
                skipped++;
                System.out.println("SKIP " + fixture + " - artificial Linux data limit");
                continue;
            }
            String failure = runFixture(fixture, target);
            if (failure == null) {
                passed++;
                System.out.println("PASS " + fixture);
            } else {
                failures.add(failure);
                System.out.println("FAIL " + fixture + " - " + failure);
            }
        }

        System.out.println();
        System.out.println("Backend fixtures (" + target.directoryName() + "): "
                + passed + "/" + fixtures.size() + " passed, " + skipped + " skipped.");
        if (!failures.isEmpty()) {
            throw new AssertionError(failures.size() + " backend fixture(s) failed.");
        }
    }

    private static String runFixture(Path fixture, TargetPlatform target) {
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
            Path objectFile = tempDirectory.resolve("fixture" + target.objectExtension());
            Path executable = tempDirectory.resolve("fixture" + target.executableExtension());
            Path stdoutFile = tempDirectory.resolve("stdout.txt");
            Path stderrFile = tempDirectory.resolve("stderr.txt");
            Files.writeString(assembly, new NasmGenerator(program, target).generate(ir));

            ProcessResult assembled = run(List.of(
                    "nasm", "-f", target.nasmFormat(), assembly.toString(), "-o", objectFile.toString()),
                    tempDirectory);
            if (assembled.exitCode() != 0) {
                return "nasm failed (" + assembled.exitCode() + "): " + assembled.stderr();
            }
            List<String> linkCommand = target.isWindows()
                    ? WindowsLinker.command(objectFile, executable)
                    : List.of("ld", "-o", executable.toString(), objectFile.toString());
            ProcessResult linked = run(linkCommand, tempDirectory);
            if (linked.exitCode() != 0) {
                return "ld failed (" + linked.exitCode() + "): " + linked.stderr();
            }

            boolean closeStdout = Files.isRegularFile(replaceExtension(fixture, ".closed_stdout"));
            boolean limitData = Files.isRegularFile(replaceExtension(fixture, ".low_data_limit"));
            List<String> executionCommand = new ArrayList<>();
            if (limitData) {
                executionCommand.addAll(List.of("bash", "-c", "ulimit -d 512; exec \"$1\"",
                        "backend-fixture", executable.toString()));
            } else {
                executionCommand.add(executable.toString());
            }
            Path argumentsFile = replaceExtension(fixture, ".args");
            if (Files.isRegularFile(argumentsFile)) {
                executionCommand.addAll(Files.readAllLines(argumentsFile));
            }
            ProcessBuilder execution = new ProcessBuilder(executionCommand)
                    .directory(tempDirectory.toFile())
                    .redirectError(stderrFile.toFile());
            if (!closeStdout) execution.redirectOutput(stdoutFile.toFile());
            Path inputFile = replaceExtension(fixture, ".in");
            if (Files.isRegularFile(inputFile)) {
                execution.redirectInput(inputFile.toFile());
            }
            Process process = execution.start();
            if (closeStdout) process.getInputStream().close();
            int exitCode = process.waitFor();
            String stdout = closeStdout ? "" : readNormalizedText(stdoutFile);
            String stderr = readNormalizedText(stderrFile);
            String expected = readNormalizedText(expectedFile);

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
                Path expectedStdoutFile = replaceExtension(fixture, ".out");
                String expectedStdout = Files.isRegularFile(expectedStdoutFile)
                        ? readNormalizedText(expectedStdoutFile)
                        : "";
                if (!stdout.equals(expectedStdout)) {
                    return "stdout mismatch before runtime failure; expected <"
                            + expectedStdout + ">, got <" + stdout + ">";
                }
                if (!stderr.startsWith(expected)) {
                    return "stderr mismatch; expected prefix <" + expected + ">, got <" + stderr + ">";
                }
                String stackTrace = stderr.substring(expected.length());
                if (stackTrace.isEmpty()) {
                    return "uncaught exception did not include a stack frame";
                }
                if (!stackTrace.matches("(?:  at [^\\n]+\\([^\\n]+\\.jvs:[0-9]+:[0-9]+\\)\\n)+")
                        || stackTrace.contains("__")) {
                    return "uncaught exception stack trace is malformed: <" + stackTrace + ">";
                }
                Path expectedTraceFile = replaceExtension(fixture, ".trace");
                if (Files.isRegularFile(expectedTraceFile)) {
                    String firstFrame = stackTrace.lines().findFirst().orElse("");
                    for (String expectedTracePart : Files.readAllLines(expectedTraceFile)) {
                        if (!expectedTracePart.isBlank() && !firstFrame.contains(expectedTracePart)) {
                            return "first runtime frame did not contain <" + expectedTracePart
                                    + ">; got <" + firstFrame + ">";
                        }
                    }
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

    private static String readNormalizedText(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n").replace('\r', '\n');
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
