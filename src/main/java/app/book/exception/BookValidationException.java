package app.book.exception;

import app.global.exception.InvalidRequestException;

/** Indicates that a book write violates a service-level validation rule (HTTP 400). */
public class BookValidationException extends InvalidRequestException {

    /**
     * Creates a book validation failure.
     *
     * @param message client-safe explanation of the broken rule
     */
    public BookValidationException(String message) {
        super(message);
    }
}
