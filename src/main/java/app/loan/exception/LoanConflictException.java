package app.loan.exception;

/**
 * Indicates that a loan operation conflicts with the current loan or copy state.
 */
public class LoanConflictException extends app.global.exception.ConflictException {

    /**
     * Creates a conflict with a client-safe explanation.
     *
     * @param message explanation of the rejected loan operation
     */
    public LoanConflictException(String message) {
        this(message, null);
    }

    /** Creates a loan conflict whose code is stable for API consumers. */
    public LoanConflictException(String message, String code) {
        super(message, code);
    }
}
