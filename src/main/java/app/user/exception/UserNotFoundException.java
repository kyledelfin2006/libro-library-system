package app.user.exception;

import app.global.exception.ResourceNotFoundException;

/** Indicates that no account exists with the requested university ID (HTTP 404). */
public class UserNotFoundException extends ResourceNotFoundException {

    /**
     * Creates a user-not-found failure.
     *
     * @param message client-safe detail
     */
    public UserNotFoundException(String message) {
        super("User", message);
    }
}
