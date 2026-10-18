package app.loan.dto;

import app.loan.entity.LoanStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Public details for one borrowing event, including its current calculated overdue state.
 */
@Schema(description = "One borrow event. Timestamps use Asia/Manila local time.")
public record LoanResponseDTO(
        @Schema(description = "Loan identifier.", example = "81")
        Long loanId,
        @Schema(description = "Borrower's university ID. Visible on own or role-authorized history.", example = "2025-4321")
        String borrowerUniversityId,
        @Schema(description = "Physical book-copy identifier.", example = "42")
        Long bookId,
        @Schema(description = "Title of the borrowed physical copy.", example = "1984")
        String bookTitle,
        @Schema(description = "Time the copy was borrowed in Asia/Manila local time.", example = "2026-10-14T15:30:00")
        LocalDateTime borrowedAt,
        @Schema(description = "Due time in Asia/Manila local time.", example = "2026-10-28T15:30:00")
        LocalDateTime dueAt,
        @Schema(description = "Return time, or null until returned.", example = "2026-10-27T11:15:00")
        LocalDateTime returnedAt,
        @Schema(description = "Lifecycle state; overdue is reported separately.", example = "ACTIVE")
        LoanStatus status,
        @Schema(description = "Calculated from active status and the current library time; not stored.", example = "false")
        boolean overdue,
        @Schema(description = "Time staff recorded this copy as lost; null otherwise.", example = "2026-11-02T09:00:00")
        LocalDateTime lostAt,
        @Schema(description = "Staff member who recorded the copy as lost; null otherwise.", example = "2025-4321")
        String lostByUniversityId
) {
}
