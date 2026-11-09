package app.global.exception;

/** A client-safe business conflict with a stable optional machine code. */
public class ConflictException extends RuntimeException {
    private final String code;

    /** Creates a coded HTTP 409 conflict. */
    public ConflictException(String message, String code) {
        super(message);
        this.code = code;
    }

    /** Returns the stable client code, when supplied. */
    public String getCode() {
        return code;
    }
}
