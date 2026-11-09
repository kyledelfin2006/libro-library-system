package app.global.exception;

/**
 * Signals that a request breaks a business or input rule the service checks (HTTP 400).
 * <p>
 * Services throw this type, not a bare {@link IllegalArgumentException}, so only rules the application owns become a
 * 400 with the message shown to the client. An {@code IllegalArgumentException} thrown by a library is a programming
 * error and falls through to the generic 500 handler, so its internal message is never returned. It still extends
 * {@code IllegalArgumentException} because it means the same thing: a caller supplied an unacceptable argument.
 * </p>
 */
public class InvalidRequestException extends IllegalArgumentException {
    private final String code;

    /**
     * Creates a validation failure with a client-safe explanation.
     *
     * @param message explanation returned to the client in the error body
     */
    public InvalidRequestException(String message) {
        this(message, null);
    }

    /** Creates a client error with an optional stable machine code. */
    public InvalidRequestException(String message, String code) {
        super(message);
        this.code = code;
    }

    /** Returns the stable client code, when supplied. */
    public String getCode() {
        return code;
    }
}
