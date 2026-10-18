package integration.loan;

import app.LibraryApplication;
import app.book.entity.Book;
import app.book.repository.BookRepository;
import app.loan.dto.LoanResponseDTO;
import app.loan.entity.Loan;
import app.loan.entity.LoanStatus;
import app.loan.repository.LoanRepository;
import app.loan.service.LoanService;
import app.user.entity.User;
import app.user.entity.enums.UserCourse;
import app.user.entity.enums.UserRole;
import app.user.repository.UserRepository;
import integration.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PostgreSQL integration checks for loan lifecycle and V4 database invariants. */
@SpringBootTest(classes = LibraryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(PostgresTestConfig.class)
@ResourceLock("postgres")
class LoanPersistenceIT {

    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private LoanService loanService;

    /** Verifies a returned loan and return time survive a PostgreSQL transaction boundary. */
    @Test
    void returningLoanPersistsTerminalHistory() {
        User borrower = createStudent();
        Book copy = createBook();
        LocalDateTime borrowedAt = LocalDateTime.now().minusDays(1);
        Loan loan = loanRepository.saveAndFlush(
                new Loan(borrower, copy, borrowedAt, borrowedAt.plusDays(14)));

        try {
            LoanResponseDTO response = loanService.returnLoan(borrower.getUniversityId(), loan.getId());
            Loan reloaded = loanRepository.findById(loan.getId()).orElseThrow();

            assertEquals(LoanStatus.RETURNED, response.status());
            assertEquals(LoanStatus.RETURNED, reloaded.getStatus());
            assertNotNull(reloaded.getReturnedAt());
            // PostgreSQL TIMESTAMP rounds Java nanoseconds to its microsecond precision.
            Duration persistenceDifference = Duration.between(response.returnedAt(), reloaded.getReturnedAt()).abs();
            assertTrue(persistenceDifference.compareTo(Duration.ofNanos(1_000)) <= 0);
        } finally {
            // Remove the history row first because its foreign keys intentionally restrict deletion.
            loanRepository.deleteById(loan.getId());
            userRepository.deleteById(borrower.getId());
            bookRepository.deleteById(copy.getId());
        }
    }

    /** Verifies PostgreSQL's partial unique index allows only one active loan per physical copy. */
    @Test
    void databaseRejectsSecondActiveLoanForSameCopy() {
        User borrower = createStudent();
        Book copy = createBook();
        LocalDateTime borrowedAt = LocalDateTime.now().minusHours(1);
        Loan firstLoan = loanRepository.saveAndFlush(
                new Loan(borrower, copy, borrowedAt, borrowedAt.plusDays(14)));

        try {
            Loan duplicate = new Loan(borrower, copy, borrowedAt.plusMinutes(1), borrowedAt.plusDays(14));

            assertThrows(DataIntegrityViolationException.class, () -> loanRepository.saveAndFlush(duplicate));
            assertEquals(1, loanRepository.countByBorrower_UniversityIdAndStatus(
                    borrower.getUniversityId(), LoanStatus.ACTIVE));
        } finally {
            // Preserve the invariant during the test, then remove fixtures in foreign-key order.
            loanRepository.deleteById(firstLoan.getId());
            userRepository.deleteById(borrower.getId());
            bookRepository.deleteById(copy.getId());
        }
    }

    /** Verifies borrower and copy rows with loan history cannot be deleted. */
    @Test
    void databaseRestrictsDeletingBorrowerAndCopyWithLoanHistory() {
        User borrower = createStudent();
        Book copy = createBook();
        LocalDateTime borrowedAt = LocalDateTime.now().minusHours(1);
        Loan loan = loanRepository.saveAndFlush(
                new Loan(borrower, copy, borrowedAt, borrowedAt.plusDays(14)));

        try {
            assertThrows(DataIntegrityViolationException.class, () -> bookRepository.deleteById(copy.getId()));
            assertThrows(DataIntegrityViolationException.class, () -> userRepository.deleteById(borrower.getId()));
            assertEquals(LoanStatus.ACTIVE, loanRepository.findById(loan.getId()).orElseThrow().getStatus());
        } finally {
            // Remove the loan before either referenced entity so historical rows are never orphaned.
            loanRepository.deleteById(loan.getId());
            userRepository.deleteById(borrower.getId());
            bookRepository.deleteById(copy.getId());
        }
    }

    /** Creates a valid student account with unique test-only identifiers. */
    private User createStudent() {
        int sequence = Math.floorMod(FIXTURE_SEQUENCE.incrementAndGet(), 10_000);
        String universityId = String.format("7%03d-%04d", sequence / 10, sequence);
        User borrower = new User();
        borrower.setUniversityId(universityId);
        borrower.setPasswordHash("test-only-hash");
        borrower.setFirstName("Integration");
        borrower.setLastName("Borrower");
        borrower.setEmail(universityId + "@example.test");
        borrower.setUserRole(UserRole.STUDENT);
        borrower.setUserCourse(UserCourse.IS);
        // Persist the role/course combination that satisfies the V2 account constraints.
        return userRepository.saveAndFlush(borrower);
    }

    /** Creates one loanable test copy with a generated database ID. */
    private Book createBook() {
        // Each scenario receives a distinct physical-copy row for isolated assertions.
        return bookRepository.saveAndFlush(
                new Book("Integration title", "Integration author", "Test", new BigDecimal("10.00")));
    }
}
