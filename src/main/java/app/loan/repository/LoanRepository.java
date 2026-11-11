package app.loan.repository;

import app.loan.entity.Loan;
import app.loan.entity.LoanStatus;
import app.loan.repository.projection.TopTitleProjection;
import app.loan.repository.projection.WeeklyLoanActivityProjection;
import app.user.entity.enums.UserRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.List;

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

    /** Queries and counts only loans visible to staff, applying every filter before pagination. */
    @EntityGraph(attributePaths = {"borrower", "book", "lostBy"})
    @Query(value = """
            SELECT l FROM Loan l JOIN l.borrower b
            WHERE b.userRole IN :visibleRoles
              AND (:status IS NULL OR l.status = :status)
              AND (:overdue = false OR (l.status = :activeStatus AND l.dueAt < :now))
              AND (:borrowerId IS NULL OR b.universityId = :borrowerId)
            """, countQuery = """
            SELECT COUNT(l) FROM Loan l JOIN l.borrower b
            WHERE b.userRole IN :visibleRoles
              AND (:status IS NULL OR l.status = :status)
              AND (:overdue = false OR (l.status = :activeStatus AND l.dueAt < :now))
              AND (:borrowerId IS NULL OR b.universityId = :borrowerId)
            """)
    Page<Loan> findStaffLoans(@Param("visibleRoles") Collection<UserRole> visibleRoles,
                              @Param("status") LoanStatus status,
                              @Param("overdue") boolean overdue,
                              @Param("activeStatus") LoanStatus activeStatus,
                              @Param("now") LocalDateTime now,
                              @Param("borrowerId") String borrowerId,
                              Pageable pageable);

    /**
     * Loads and write-locks a loan while staff closes it as lost or the borrower returns it.
     *
     * @param id loan identifier
     * @return the locked loan, if found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Loan l WHERE l.id = :id")
    Optional<Loan> findByIdForUpdate(@Param("id") Long id);

    /** Groups in-scope checkout and return events by stored Manila calendar week into typed rows. */
    @Query(value = """
            SELECT CAST(date_trunc('week', e.occurred_at) AS date) AS "weekStart",
                   SUM(CASE WHEN e.kind = 1 THEN 1 ELSE 0 END) AS checkouts,
                   SUM(CASE WHEN e.kind = 2 THEN 1 ELSE 0 END) AS "returns"
            FROM (SELECT l.user_id, l.borrowed_at AS occurred_at, 1 AS kind FROM loans l
                  UNION ALL
                  SELECT l.user_id, l.returned_at AS occurred_at, 2 AS kind FROM loans l
                  WHERE l.returned_at IS NOT NULL) e
            JOIN users u ON u.id = e.user_id
            WHERE e.occurred_at >= :start AND e.occurred_at < :end
              AND (u.user_role IN ('STUDENT', 'FACULTY')
                   OR (:librarian = true AND u.user_role = 'ADMINISTRATOR'))
            GROUP BY "weekStart" ORDER BY "weekStart"
            """, nativeQuery = true)
    List<WeeklyLoanActivityProjection> weeklyActivity(@Param("start") LocalDateTime start,
                                                       @Param("end") LocalDateTime end,
                                                       @Param("librarian") boolean librarian);

    /** Ranks typed title and author groups by checkout count with deterministic ties. */
    @Query(value = """
            SELECT b.title, b.author, COUNT(*) AS checkouts FROM loans l
            JOIN books b ON b.id = l.book_id JOIN users u ON u.id = l.user_id
            WHERE l.borrowed_at >= :start AND l.borrowed_at < :end
              AND (u.user_role IN ('STUDENT', 'FACULTY')
                   OR (:librarian = true AND u.user_role = 'ADMINISTRATOR'))
            GROUP BY b.title, b.author ORDER BY checkouts DESC, b.title, b.author LIMIT :limit
            """, nativeQuery = true)
    List<TopTitleProjection> topTitles(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                       @Param("librarian") boolean librarian, @Param("limit") int limit);
}
