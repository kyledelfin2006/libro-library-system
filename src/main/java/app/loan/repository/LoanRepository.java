package app.loan.repository;

import app.loan.entity.Loan;
import app.loan.entity.LoanStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Persistence operations for loan aggregate roots.
 */
public interface LoanRepository extends JpaRepository<Loan, Long> {

    /**
     * Counts a borrower's active loans for the three-loan policy.
     *
     * @param universityId borrower university ID
     * @param status lifecycle state to count
     * @return number of matching loans
     */
    long countByBorrower_UniversityIdAndStatus(String universityId, LoanStatus status);

    /**
     * Checks whether a borrower has an active loan past its due time.
     *
     * @param universityId borrower university ID
     * @param status active state to search
     * @param now current library-local time
     * @return true when at least one active loan is overdue
     */
    boolean existsByBorrower_UniversityIdAndStatusAndDueAtBefore(
            String universityId, LoanStatus status, LocalDateTime now);

    /**
     * Checks whether the physical copy is already checked out.
     *
     * @param bookId physical copy identifier
     * @param status active state to search
     * @return true when an active loan exists for this copy
     */
    boolean existsByBook_IdAndStatus(Long bookId, LoanStatus status);

    /**
     * Returns a paginated history for the borrower with all response relations loaded.
     *
     * @param universityId borrower university ID
     * @param pageable bounded page, size, and approved sort fields
     * @return the requested loan-history page
     */
    @EntityGraph(attributePaths = {"borrower", "book", "lostBy"})
    Page<Loan> findByBorrower_UniversityId(String universityId, Pageable pageable);

    /**
     * Loads and write-locks a loan while staff closes it as lost or the borrower returns it.
     *
     * @param id loan identifier
     * @return the locked loan, if found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Loan l WHERE l.id = :id")
    Optional<Loan> findByIdForUpdate(@Param("id") Long id);
}
