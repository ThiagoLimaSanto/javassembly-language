package compiler.exceptions;

import java.io.IOException;
import java.nio.file.Path;

public final class OutputException extends IOException {
    private final Path path;

    public OutputException(String message, Path path, Throwable cause) {
        super(message + (path == null ? "" : " Caminho: '" + path + "'."), cause);
        this.path = path;
    }

    public Path getPath() {
        return path;
    }
}
