package app.loan.exception;

/**
 * Indicates that a loan operation conflicts with the current loan or copy state.
 */
public class LoanConflictException extends RuntimeException {

    /**
     * Creates a conflict with a client-safe explanation.
     *
     * @param message explanation of the rejected loan operation
     */
    public LoanConflictException(String message) {
        super(message);
    }
}
