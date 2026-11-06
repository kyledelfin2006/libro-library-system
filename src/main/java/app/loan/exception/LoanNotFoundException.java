package app.loan.exception;

import app.global.exception.ResourceNotFoundException;

/**
 * Indicates that the requested loan does not exist or is not visible to the caller (HTTP 404).
 */
public class LoanNotFoundException extends ResourceNotFoundException {

    /**
     * Creates a client-safe loan-not-found exception.
     *
     * @param message explanation that does not reveal another borrower's loan
     */
    public LoanNotFoundException(String message) {
        super("Loan", message);
    }
}
