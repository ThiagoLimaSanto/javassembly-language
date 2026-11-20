package compiler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import compiler.exceptions.OutputException;

/** Builds the MinGW-w64 linker command used for Windows executables. */
public final class WindowsLinker {
    private WindowsLinker() {
    }

    public static List<String> command(Path object, Path executable) throws IOException {
        boolean windowsHost = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).startsWith("windows");
        String configuredLinker = System.getenv("JAVASSEMBLY_WINDOWS_LINKER");
        String linker = configuredLinker == null || configuredLinker.isBlank()
                ? windowsHost ? "ld.exe" : "x86_64-w64-mingw32-ld"
                : configuredLinker;

        List<String> command = new ArrayList<>();
        if (windowsHost) {
            LinkerConfig config = findWindowsLinkerConfig(linker, executable);
            command.add(config.linker().toString());
            command.add("-L" + config.libraryDirectory());
        } else {
            command.add(linker);
        }
        command.addAll(List.of("-e", "start", "--subsystem", "console", "-o",
                executable.toString(), object.toString(), "-lshell32", "-lkernel32"));
        return List.copyOf(command);
    }

    private static LinkerConfig findWindowsLinkerConfig(String linker, Path outputPath) throws IOException {
        List<Path> linkerPaths = findExecutablePaths(linker);
        String configuredLibraryDirectory = System.getenv("JAVASSEMBLY_WINDOWS_LIB_DIR");
        if (configuredLibraryDirectory != null && !configuredLibraryDirectory.isBlank()) {
            Path directory = Path.of(configuredLibraryDirectory).toAbsolutePath().normalize();
            if (hasRequiredWindowsLibraries(directory)) {
                Path executablePath = linkerPaths.isEmpty() ? Path.of(linker) : linkerPaths.get(0);
                return new LinkerConfig(executablePath, directory);
            }
            throw new OutputException("JAVASSEMBLY_WINDOWS_LIB_DIR precisa conter libkernel32.a e "
                    + "libshell32.a.", outputPath, null);
        }

        for (Path linkerPath : linkerPaths) {
            Path binDirectory = linkerPath.getParent();
            if (binDirectory == null) continue;
            Path toolchainRoot = binDirectory.getParent();
            if (toolchainRoot == null) continue;

            List<Path> candidates = List.of(
                    toolchainRoot.resolve("lib"),
                    toolchainRoot.resolve("x86_64-w64-mingw32").resolve("lib"),
                    binDirectory.resolve("..\\lib").normalize(),
                    binDirectory.resolve("..\\x86_64-w64-mingw32\\lib").normalize());
            for (Path candidate : candidates) {
                if (hasRequiredWindowsLibraries(candidate)) {
                    return new LinkerConfig(linkerPath, candidate.toAbsolutePath().normalize());
                }
            }
        }

        throw new OutputException("Não encontrei libkernel32.a e libshell32.a nas pastas padrão do MinGW-w64. "
                + "Configure JAVASSEMBLY_WINDOWS_LIB_DIR com a pasta que contém esses arquivos.", outputPath, null);
    }

    private static boolean hasRequiredWindowsLibraries(Path directory) {
        return Files.isRegularFile(directory.resolve("libkernel32.a"))
                && Files.isRegularFile(directory.resolve("libshell32.a"));
    }

    private static List<Path> findExecutablePaths(String executable) {
        Path requestedPath = Path.of(executable);
        if (requestedPath.isAbsolute() || executable.contains("/") || executable.contains("\\")) {
            return Files.isRegularFile(requestedPath)
                    ? List.of(requestedPath.toAbsolutePath().normalize())
                    : List.of();
        }

        Set<Path> matches = new LinkedHashSet<>();
        String pathValue = System.getenv("PATH");
        if (pathValue == null) return List.of();
        for (String entry : pathValue.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (entry.isBlank()) continue;
            Path directory = Path.of(entry.replace("\"", ""));
            Path candidate = directory.resolve(executable);
            if (Files.isRegularFile(candidate)) {
                matches.add(candidate.toAbsolutePath().normalize());
            }
        }
        return List.copyOf(matches);
    }

    private record LinkerConfig(Path linker, Path libraryDirectory) {
    }
}
