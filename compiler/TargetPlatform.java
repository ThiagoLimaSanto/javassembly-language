package compiler;

import compiler.exceptions.CommandLineException;

/** Code generation target selected for a compilation. */
public enum TargetPlatform {
    LINUX_X64("-l", "linux-x64", "elf64", ".o", ""),
    WINDOWS_X64("-w", "windows-x64", "win64", ".obj", ".exe");

    private final String flag;
    private final String directoryName;
    private final String nasmFormat;
    private final String objectExtension;
    private final String executableExtension;

    TargetPlatform(
            String flag,
            String directoryName,
            String nasmFormat,
            String objectExtension,
            String executableExtension) {
        this.flag = flag;
        this.directoryName = directoryName;
        this.nasmFormat = nasmFormat;
        this.objectExtension = objectExtension;
        this.executableExtension = executableExtension;
    }

    public String flag() {
        return flag;
    }

    public String directoryName() {
        return directoryName;
    }

    public String nasmFormat() {
        return nasmFormat;
    }

    public String objectExtension() {
        return objectExtension;
    }

    public String executableExtension() {
        return executableExtension;
    }

    public boolean isWindows() {
        return this == WINDOWS_X64;
    }

    public static TargetPlatform fromFlag(String flag) {
        for (TargetPlatform target : values()) {
            if (target.flag.equals(flag)) return target;
        }
        throw new CommandLineException(
                "Destino inválido '" + flag + "'. Use -l (Linux x86-64) ou -w (Windows x86-64).");
    }
}
