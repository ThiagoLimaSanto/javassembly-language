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
import src.exceptions.ImportException;
import src.exceptions.SourceProjectException;
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
            throw new SourceProjectException("Arquivo-fonte não encontrado.", normalizedFile);
        }

        ProgramRecord unit = parseProjectFile(normalizedFile);
        Path sourceRoot = findSourceRoot(normalizedFile, unit.packageName());

        for (String importedType : unit.imports()) {
            if (importedType.startsWith("static:")) {
                loadStaticImport(importedType.substring("static:".length()), unit, sourceRoot);
                continue;
            }
            if (importedType.endsWith(".*")) {
                String importedPackage = importedType.substring(0, importedType.length() - 2);
                Path importedDirectory = sourceRoot.resolve(importedPackage.replace('.', '/')).normalize();
                loadPackageSources(importedDirectory);
                continue;
            }
            if (!importedType.contains(".")) {
                throw new ImportException(
                        "O import '" + importedType + "' está incompleto; informe o pacote e o tipo, por exemplo 'pacote.Tipo'.",
                        1,
                        1);
            }

            Path importedFile = sourceRoot.resolve(importedType.replace('.', '/') + ".jvs").normalize();
            requireFile(importedFile);
            ProgramRecord importedUnit = parseProjectFile(importedFile);
            String expectedPackage = packageOf(importedType);
            String expectedType = simpleName(importedType);

            if (!expectedPackage.equals(importedUnit.packageName())) {
                throw new ImportException(
                        "O arquivo resolvido para o import '" + importedType
                                + "' declara o package '" + importedUnit.packageName()
                                + "', mas era esperado '" + expectedPackage + "'.",
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
                throw new ImportException(
                        "O import '" + importedType + "' aponta para um arquivo que não declara essa classe ou interface.",
                        1,
                        1);
            }

            if (!expectedPackage.equals(unit.packageName()) && !typeIsPublic) {
                throw new ImportException(
                        "O tipo '" + importedType
                                + "' não é público; imports entre packages exigem uma classe ou interface public.",
                        1,
                        1);
            }

            loadFile(importedFile);
        }

        // Qualified type names are valid without an import. Resolve their conventional
        // package path when the source is part of this project tree.
        java.util.regex.Matcher qualifiedNames = java.util.regex.Pattern
                .compile("\\b([\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*"
                        + "(?:\\.[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)+)\\b")
                .matcher(readSource(normalizedFile));
        while (qualifiedNames.find()) {
            String candidate = qualifiedNames.group(1);
            if (candidate.startsWith("static:")) continue;
            Path candidateFile = sourceRoot.resolve(candidate.replace('.', '/') + ".jvs").normalize();
            if (Files.isRegularFile(candidateFile) && !candidateFile.equals(normalizedFile)) {
                loadFile(candidateFile);
            }
        }

        Path packageDirectory = sourceRoot.resolve(unit.packageName().replace('.', '/'));
        loadPackageSources(packageDirectory, unit.packageName().isEmpty()
                ? readSource(normalizedFile) : null);

        classes.addAll(unit.classes());
        interfaces.addAll(unit.interfaces());
        registers.addAll(unit.registers());
    }

    private Path requireFile(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new SourceProjectException("Arquivo-fonte importado não encontrado.", path);
        }
        return path;
    }

    private ProgramRecord parse(String source) {
        return Frontend.parse(source);
    }

    private ProgramRecord parseProjectFile(Path file) throws IOException {
        ProgramRecord cached = parsedFiles.get(file);
        if (cached != null) {
            return cached;
        }

        String source = readSource(file);
        ProgramRecord preliminary = parse(source);
        Path sourceRoot = findSourceRoot(file, preliminary.packageName());
        Set<String> knownTypes = indexVisibleSourceTypes(sourceRoot, preliminary, source);
        ProgramRecord resolved = Frontend.parse(source, knownTypes);
        parsedFiles.put(file, resolved);
        return resolved;
    }

    private Set<String> indexVisibleSourceTypes(
            Path sourceRoot,
            ProgramRecord unit,
            String source) throws IOException {
        Set<String> packagesToIndex = new HashSet<>();
        packagesToIndex.add(unit.packageName());
        for (String importedName : unit.imports()) {
            if (!importedName.startsWith("static:") && importedName.endsWith(".*")) {
                packagesToIndex.add(importedName.substring(0, importedName.length() - 2));
            }
        }

        Set<String> typeNames = new HashSet<>();
        for (String packageName : packagesToIndex) {
            Path directory = sourceRoot.resolve(packageName.replace('.', '/'));
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> files = Files.list(directory)) {
                for (Path sourceFile : files
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".jvs"))
                        .filter(path -> !packageName.isEmpty()
                                || sourceMentionsTypeFile(source, path))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList()) {
                    ProgramRecord sourceUnit = parse(readSource(sourceFile));
                    sourceUnit.classes().forEach(type -> typeNames.add(type.name()));
                    sourceUnit.interfaces().forEach(type -> typeNames.add(type.name()));
                }
            }
        }
        return typeNames;
    }

    private void loadPackageSources(Path packageDirectory) throws IOException {
        loadPackageSources(packageDirectory, null);
    }

    private void loadPackageSources(Path packageDirectory, String sourceContext) throws IOException {
        if (!Files.isDirectory(packageDirectory)) {
            throw new SourceProjectException("Diretório do package não encontrado.", packageDirectory);
        }
        try (Stream<Path> entries = Files.list(packageDirectory)) {
            for (Path packageSource : entries
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jvs"))
                    .filter(path -> sourceContext == null
                            || sourceMentionsTypeFile(sourceContext, path))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList()) {
                loadFile(packageSource);
            }
        }
    }

    private void loadStaticImport(
            String target,
            ProgramRecord importingUnit,
            Path sourceRoot) throws IOException {
        boolean onDemand = target.endsWith(".*");
        String memberName = onDemand ? "*" : simpleName(target);
        String ownerName = onDemand
                ? target.substring(0, target.length() - 2)
                : target.substring(0, target.lastIndexOf('.'));
        if (!ownerName.contains(".")) {
            ownerName = importingUnit.packageName().isEmpty()
                    ? ownerName
                    : importingUnit.packageName() + "." + ownerName;
        }

        Path ownerFile = sourceRoot.resolve(ownerName.replace('.', '/') + ".jvs").normalize();
        requireFile(ownerFile);
        ProgramRecord ownerUnit = parseProjectFile(ownerFile);
        final String resolvedOwnerName = ownerName;
        String ownerPackage = packageOf(ownerName);
        if (!ownerPackage.equals(ownerUnit.packageName())) {
            throw new ImportException(
                    "O arquivo de '" + ownerName + "' declara o package '"
                            + ownerUnit.packageName() + "', mas era esperado '"
                            + ownerPackage + "'.",
                    1,
                    1);
        }

        ClassRecord ownerClass = ownerUnit.classes().stream()
                .filter(type -> type.name().equals(resolvedOwnerName))
                .findFirst()
                .orElse(null);
        InterfaceRecord ownerInterface = ownerUnit.interfaces().stream()
                .filter(type -> type.name().equals(resolvedOwnerName))
                .findFirst()
                .orElse(null);
        if (ownerClass == null && ownerInterface == null) {
            throw new ImportException(
                    "O import estático aponta para um tipo inexistente: '" + ownerName + "'.",
                    1,
                    1);
        }
        TokenType ownerVisibility = ownerClass != null
                ? ownerClass.visibility() : ownerInterface.visibility();
        if (!ownerPackage.equals(importingUnit.packageName())
                && ownerVisibility != TokenType.PUBLIC) {
            throw new ImportException(
                    "O tipo '" + ownerName + "' não é público e não pode ser importado de outro package.",
                    1,
                    1);
        }

        if (!onDemand && !hasAccessibleStaticMember(
                ownerClass, ownerInterface, memberName, ownerPackage,
                importingUnit.packageName())) {
            throw new ImportException(
                    "O tipo '" + ownerName + "' não declara um membro static acessível chamado '"
                            + memberName + "'.",
                    1,
                    1);
        }
        loadFile(ownerFile);
    }

    private boolean hasAccessibleStaticMember(
            ClassRecord ownerClass,
            InterfaceRecord ownerInterface,
            String memberName,
            String ownerPackage,
            String importingPackage) {
        if (ownerClass != null) {
            boolean fieldExists = ownerClass.attributes().stream().anyMatch(field ->
                    field.name().equals(memberName)
                            && field.isStatic()
                            && isImportAccessible(field.visibility(), ownerPackage, importingPackage));
            boolean methodExists = ownerClass.functions().stream().anyMatch(method ->
                    method.name().equals(memberName)
                            && method.isStatic()
                            && isImportAccessible(method.visibility(), ownerPackage, importingPackage));
            return fieldExists || methodExists;
        }
        return ownerInterface.functions().stream().anyMatch(method ->
                method.name().equals(memberName)
                        && method.isStatic()
                        && isImportAccessible(method.visibility(), ownerPackage, importingPackage));
    }

    private boolean isImportAccessible(
            TokenType visibility,
            String ownerPackage,
            String importingPackage) {
        return visibility == TokenType.PUBLIC
                || ownerPackage.equals(importingPackage)
                        && visibility != TokenType.PRIVATE;
    }

    private boolean sourceMentionsTypeFile(String source, Path sourceFile) {
        String fileName = sourceFile.getFileName().toString();
        String typeName = fileName.substring(0, fileName.length() - ".jvs".length());
        return java.util.regex.Pattern.compile("\\b"
                + java.util.regex.Pattern.quote(typeName) + "\\b")
                .matcher(source)
                .find();
    }

    private String readSource(Path file) throws IOException {
        try {
            return Files.readString(file);
        } catch (IOException cause) {
            throw new SourceProjectException("Não foi possível ler o arquivo-fonte.", file, cause);
        }
    }

    private Path findSourceRoot(Path file, String packageName) throws IOException {
        Path root = file.getParent();
        if (root == null) {
            throw new SourceProjectException("Não foi possível determinar o diretório do arquivo-fonte.", file);
        }

        if (packageName.isEmpty()) {
            return root;
        }

        String[] segments = packageName.split("\\.");
        for (int index = segments.length - 1; index >= 0; index--) {
            Path directoryName = root.getFileName();
            if (directoryName == null || !directoryName.toString().equals(segments[index])) {
                throw new SourceProjectException(
                        "O caminho do arquivo não corresponde ao package declarado '" + packageName + "'.", file);
            }
            root = root.getParent();
            if (root == null) {
                throw new SourceProjectException("Não foi possível determinar a raiz dos arquivos-fonte.", file);
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
