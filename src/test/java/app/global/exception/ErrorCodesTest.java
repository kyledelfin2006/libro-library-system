package app.global.exception;

import app.loan.exception.LoanConflictException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Stable client codes coexist with the existing error title, details, and status. */
class ErrorCodesTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void codedConflictAndValidationKeepExistingFields() {
        var conflict = handler.handleLoanConflict(new LoanConflictException(
                "Book copy is already borrowed", "LOAN_COPY_UNAVAILABLE"));
        assertEquals(409, conflict.getBody().getStatusCode());
        assertEquals("Book copy is already borrowed", conflict.getBody().getDetails());
        assertEquals("LOAN_COPY_UNAVAILABLE", conflict.getBody().getCode());

        var validation = handler.handleInvalidRequest(new InvalidRequestException(
                "Current password is incorrect", "PASSWORD_CURRENT_INCORRECT"));
        assertEquals(400, validation.getBody().getStatusCode());
        assertEquals("PASSWORD_CURRENT_INCORRECT", validation.getBody().getCode());
        var history = handler.handleLoanConflict(new ConflictException(
                "Account has loan history and must be retained", "RESOURCE_HAS_HISTORY"));
        assertEquals("Resource conflict", history.getBody().getError());
        assertEquals("RESOURCE_HAS_HISTORY", history.getBody().getCode());
        assertNull(handler.handleInvalidRequest(new InvalidRequestException("Other error")).getBody().getCode());
    }
}
