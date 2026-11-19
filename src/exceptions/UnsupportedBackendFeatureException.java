package src.exceptions;

/** A valid source construct that the current backend cannot generate yet. */
public final class UnsupportedBackendFeatureException extends BackendException {
    public UnsupportedBackendFeatureException(String message) {
        super(message);
    }
}
