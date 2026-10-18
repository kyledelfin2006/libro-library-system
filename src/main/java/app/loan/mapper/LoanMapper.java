package app.loan.mapper;

import app.loan.dto.LoanResponseDTO;
import app.loan.entity.Loan;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Maps the loan entity into its public response contract.
 */
@Component
public class LoanMapper {

    /**
     * Builds a response and calculates whether the loan is overdue at the supplied time.
     *
     * @param loan persisted loan entity
     * @param now current Asia/Manila local time
     * @return response DTO without exposing JPA entities
     */
    public LoanResponseDTO toResponse(Loan loan, LocalDateTime now) {
        // Read the associated borrower, copy, and staff audit data inside the service transaction.
        return new LoanResponseDTO(
                loan.getId(),
                loan.getBorrower().getUniversityId(),
                loan.getBook().getId(),
                loan.getBook().getTitle(),
                loan.getBorrowedAt(),
                loan.getDueAt(),
                loan.getReturnedAt(),
                loan.getStatus(),
                loan.isOverdue(now),
                loan.getLostAt(),
                loan.getLostBy() == null ? null : loan.getLostBy().getUniversityId()
        );
    }
}
