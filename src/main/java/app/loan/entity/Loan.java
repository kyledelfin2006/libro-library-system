package app.loan.entity;

import app.book.entity.Book;
import app.user.entity.User;
import app.loan.exception.LoanConflictException;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One checkout event connecting one patron to one physical book copy.
 */
@Entity
@Table(name = "loans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Access(AccessType.FIELD)
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User borrower;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "borrowed_at", nullable = false, updatable = false)
    private LocalDateTime borrowedAt;

    @Column(name = "due_at", nullable = false, updatable = false)
    private LocalDateTime dueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LoanStatus status = LoanStatus.ACTIVE;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "lost_at")
    private LocalDateTime lostAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lost_by_user_id")
    private User lostBy;

    /**
     * Creates a new active loan for an authenticated patron and copy.
     *
     * @param borrower the student or faculty member borrowing the copy
     * @param book the physical copy being borrowed
     * @param borrowedAt the Manila local time at checkout
     * @param dueAt the Manila local time when the copy is due
     */
    public Loan(User borrower, Book book, LocalDateTime borrowedAt, LocalDateTime dueAt) {
        this.borrower = borrower;
        this.book = book;
        this.borrowedAt = borrowedAt;
        this.dueAt = dueAt;
        this.status = LoanStatus.ACTIVE;
    }

    /**
     * Returns whether this borrowing event is still active.
     *
     * @return true only while the loan has not been returned or marked lost
     */
    public boolean isActive() {
        return status == LoanStatus.ACTIVE;
    }

    /**
     * Calculates whether this active loan has passed its deadline.
     *
     * @param now the current library-local time
     * @return true when this loan is active and the current time is after its due time
     */
    public boolean isOverdue(LocalDateTime now) {
        // Overdue is derived from the clock and never stored as a boolean.
        return isActive() && now.isAfter(dueAt);
    }

    /**
     * Records that the borrower returned this copy.
     *
     * @param returnedAt the Manila local time the copy was returned
     * @throws LoanConflictException if the loan is already returned or lost
     */
    public void markReturned(LocalDateTime returnedAt) {
        // Only an active borrowing event can transition to returned.
        requireActive();
        this.returnedAt = returnedAt;
        this.status = LoanStatus.RETURNED;
    }

    /**
     * Records that authorized library staff resolved this loan as a lost copy.
     *
     * @param staff the authenticated librarian or library assistant recording the loss
     * @param lostAt the Manila local time the loss was recorded
     * @throws LoanConflictException if the loan is already returned or lost
     */
    public void markLost(User staff, LocalDateTime lostAt) {
        // A lost state closes the borrowing event without claiming the book was returned.
        requireActive();
        this.lostAt = lostAt;
        this.lostBy = staff;
        this.status = LoanStatus.LOST;
    }

    /**
     * Rejects a second terminal transition for this loan.
     *
     * @throws LoanConflictException if the loan is no longer active
     */
    private void requireActive() {
        // Keep the transition rule in the entity so every service path follows it.
        if (!isActive()) {
            throw new LoanConflictException("Loan is no longer active");
        }
    }
}
