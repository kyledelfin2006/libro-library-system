package app.book.exception;

import app.global.exception.ResourceNotFoundException;

/** Indicates that no physical book copy exists with the requested ID (HTTP 404). */
public class BookNotFoundException extends ResourceNotFoundException {

    /**
     * Creates a book-not-found failure.
     *
     * @param message client-safe detail, usually naming the missing ID
     */
    public BookNotFoundException(String message) {
        super("Book", message);
    }
}
