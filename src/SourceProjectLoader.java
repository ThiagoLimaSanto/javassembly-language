package src;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.Comparator;
import java.util.stream.Stream;

import src.ast.records.ClassRecord;
import src.ast.records.InterfaceRecord;
import src.ast.records.ProgramRecord;
import src.ast.records.RegisterDeclarationRecord;
import src.exceptions.SemanticException;
import src.lexer.TokenType;

final class SourceProjectLoader {
    private final Set<Path> loadedFiles = new HashSet<>();
    private final Map<Path, ProgramRecord> parsedFiles = new HashMap<>();
    private final List<ClassRecord> classes = new ArrayList<>();
    private final List<InterfaceRecord> interfaces = new ArrayList<>();
    private final List<RegisterDeclarationRecord> registers = new ArrayList<>();

    ProgramRecord load(Path entryFile) throws IOException {
        loadFile(entryFile.toAbsolutePath().normalize());
        return new ProgramRecord(classes, interfaces, registers);
    }

    private void loadFile(Path file) throws IOException {
        Path normalizedFile = file.toAbsolutePath().normalize();
        if (!loadedFiles.add(normalizedFile)) {
            return;
        }

        if (!Files.isRegularFile(normalizedFile)) {
            throw new IOException("Arquivo-fonte importado não encontrado: " + normalizedFile);
        }

        ProgramRecord unit = parsedFiles.get(normalizedFile);
        if (unit == null) {
            unit = parse(Files.readString(normalizedFile));
            parsedFiles.put(normalizedFile, unit);
        }
        Path sourceRoot = findSourceRoot(normalizedFile, unit.packageName());

        for (String importedType : unit.imports()) {
            if (!importedType.contains(".")) {
                throw new SemanticException(
                        "Um import precisa informar pacote e tipo: '" + importedType + "'.",
                        1,
                        1);
            }

            Path importedFile = sourceRoot.resolve(importedType.replace('.', '/') + ".jvs").normalize();
            requireFile(importedFile);
            ProgramRecord importedUnit = parsedFiles.get(importedFile);
            if (importedUnit == null) {
                importedUnit = parse(Files.readString(importedFile));
                parsedFiles.put(importedFile, importedUnit);
            }
            String expectedPackage = packageOf(importedType);
            String expectedType = simpleName(importedType);

            if (!expectedPackage.equals(importedUnit.packageName())) {
                throw new SemanticException(
                        "O pacote declarado em '" + importedFile
                                + "' não corresponde ao import '" + importedType + "'.",
                        1,
                        1);
            }

            boolean typeExists = false;
            boolean typeIsPublic = false;
            for (ClassRecord classNode : importedUnit.classes()) {
                if (!simpleName(classNode.name()).equals(expectedType)) {
                    continue;
                }
                typeExists = true;
                typeIsPublic |= classNode.visibility() == TokenType.PUBLIC;
            }
            for (InterfaceRecord interfaceNode : importedUnit.interfaces()) {
                if (!simpleName(interfaceNode.name()).equals(expectedType)) {
                    continue;
                }
                typeExists = true;
                typeIsPublic |= interfaceNode.visibility() == TokenType.PUBLIC;
            }
            if (!typeExists) {
                throw new SemanticException(
                        "O arquivo importado não declara o tipo '" + importedType + "'.",
                        1,
                        1);
            }

            if (!expectedPackage.equals(unit.packageName()) && !typeIsPublic) {
                throw new SemanticException(
                        "O tipo '" + importedType
                                + "' não é público e não pode ser importado de outro package.",
                        1,
                        1);
            }

            loadFile(importedFile);
        }

        if (!unit.packageName().isEmpty()) {
            Path packageDirectory = sourceRoot.resolve(
                    unit.packageName().replace('.', '/'));
            try (Stream<Path> entries = Files.list(packageDirectory)) {
                for (Path packageSource : entries
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".jvs"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList()) {
                    loadFile(packageSource);
                }
            }
        }

        classes.addAll(unit.classes());
        interfaces.addAll(unit.interfaces());
        registers.addAll(unit.registers());
    }

    private Path requireFile(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Arquivo-fonte importado não encontrado: " + path);
        }
        return path;
    }

    private ProgramRecord parse(String source) {
        return Frontend.parse(source);
    }

    private Path findSourceRoot(Path file, String packageName) throws IOException {
        Path root = file.getParent();
        if (root == null) {
            throw new IOException("Não foi possível determinar o diretório do arquivo: " + file);
        }

        if (packageName.isEmpty()) {
            return root;
        }

        String[] segments = packageName.split("\\.");
        for (int index = segments.length - 1; index >= 0; index--) {
            Path directoryName = root.getFileName();
            if (directoryName == null || !directoryName.toString().equals(segments[index])) {
                throw new IOException(
                        "O caminho do arquivo '" + file
                                + "' não corresponde ao package '" + packageName + "'.");
            }
            root = root.getParent();
            if (root == null) {
                throw new IOException("Não foi possível determinar a raiz dos fontes para " + file);
            }
        }

        return root;
    }

    private String packageOf(String qualifiedName) {
        int separator = qualifiedName.lastIndexOf('.');
        return separator < 0 ? "" : qualifiedName.substring(0, separator);
    }

    private String simpleName(String qualifiedName) {
        int separator = qualifiedName.lastIndexOf('.');
        return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
    }
}
