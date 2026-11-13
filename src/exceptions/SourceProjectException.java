package src.exceptions;

import java.io.IOException;
import java.nio.file.Path;

public class SourceProjectException extends IOException {
    private final Path path;

    public SourceProjectException(String message, Path path) {
        super(message + (path == null ? "" : " Caminho: '" + path + "'."));
        this.path = path;
    }

    public SourceProjectException(String message, Path path, Throwable cause) {
        super(message + (path == null ? "" : " Caminho: '" + path + "'."), cause);
        this.path = path;
    }

    public Path getPath() {
        return path;
    }
}
