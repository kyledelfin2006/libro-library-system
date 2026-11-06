package app.global.exception;

/**
 * Base type for "the requested resource does not exist (or is not visible to the caller)".
 * <p>
 * Each feature subclasses it with its own resource name, and {@link GlobalExceptionHandler} turns every subclass into
 * the same HTTP 404 shape. Adding a new resource therefore needs no new handler method. The message must be safe to
 * show to the client; for example, loans use one message for "missing" and "belongs to another borrower".
 * </p>
 */
public abstract class ResourceNotFoundException extends RuntimeException {

    private final String resourceName;

    /**
     * Creates a not-found failure for one resource type.
     *
     * @param resourceName human-readable resource type used in the error title, such as {@code "Book"}
     * @param message client-safe detail returned in the error body
     */
    protected ResourceNotFoundException(String resourceName, String message) {
        super(message);
        this.resourceName = resourceName;
    }

    /**
     * Returns the resource type used to build the error title (for example {@code "Book not found"}).
     *
     * @return the resource type name
     */
    public String getResourceName() {
        return resourceName;
    }
}
