package app.book.exceptions;

/** Indicates that a book write violates a service-level validation rule. */
public class BookValidationException extends RuntimeException {

    public BookValidationException(String message) {
        super(message);
    }
}
