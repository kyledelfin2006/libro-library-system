package app.loan.exception;

/**
 * Indicates that the requested loan does not exist or is not visible to the caller.
 */
public class LoanNotFoundException extends RuntimeException {

    /**
     * Creates a client-safe loan-not-found exception.
     *
     * @param message explanation that does not reveal another borrower's loan
     */
    public LoanNotFoundException(String message) {
        super(message);
    }
}
