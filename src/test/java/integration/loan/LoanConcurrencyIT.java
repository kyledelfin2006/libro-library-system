package integration.loan;

import app.LibraryApplication;
import app.book.entity.Book;
import app.book.repository.BookRepository;
import app.loan.entity.LoanStatus;
import app.loan.exception.LoanConflictException;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Proves the loan invariants hold when checkouts genuinely overlap on PostgreSQL.
 * <p>
 * These tests deliberately are NOT {@code @Transactional}: each worker thread must run its own committed transaction,
 * otherwise the pessimistic row locks in {@code LoanService.borrow} would never contend. A start latch releases all
 * workers at once to maximize overlap. If a lock mode or transaction boundary regresses, these tests fail.
 * </p>
 */
@SpringBootTest(classes = LibraryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(PostgresTestConfig.class)
@ResourceLock("postgres")
class LoanConcurrencyIT {

    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger(5000);

    @Autowired
    private LoanService loanService;
    @Autowired
    private LoanRepository loanRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;

    /** A borrower with two active loans who races for five copies may end with exactly three. */
    @Test
    void borrowerCannotExceedThreeActiveLoansUnderConcurrency() throws Exception {
        User borrower = createStudent();
        List<Book> copies = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            copies.add(createBook());
        }
        try {
            // Two loans already exist, so exactly one of the five concurrent checkouts may succeed.
            loanService.borrow(borrower.getUniversityId(), copies.get(0).getId());
            loanService.borrow(borrower.getUniversityId(), copies.get(1).getId());

            List<Callable<Object>> attempts = new ArrayList<>();
            for (int i = 2; i < 7; i++) {
                Long bookId = copies.get(i).getId();
                attempts.add(() -> loanService.borrow(borrower.getUniversityId(), bookId));
            }
            Outcome outcome = raceAll(attempts);

            assertEquals(1, outcome.successes());
            assertEquals(4, outcome.conflicts());
            assertEquals(3, loanRepository.countByBorrower_UniversityIdAndStatus(
                    borrower.getUniversityId(), LoanStatus.ACTIVE));
        } finally {
            cleanup(borrower, copies);
        }
    }

    /** Two borrowers racing for the same copy: exactly one wins. */
    @Test
    void sameCopyCannotBeBorrowedByTwoBorrowersConcurrently() throws Exception {
        User first = createStudent();
        User second = createStudent();
        Book copy = createBook();
        try {
            Outcome outcome = raceAll(List.of(
                    () -> loanService.borrow(first.getUniversityId(), copy.getId()),
                    () -> loanService.borrow(second.getUniversityId(), copy.getId())));

            assertEquals(1, outcome.successes());
            assertEquals(1, outcome.conflicts());
        } finally {
            cleanup(first, List.of(copy));
            userRepository.deleteById(second.getId());
        }
    }

    /** Result counts for a concurrent run; any other exception type fails the test immediately. */
    private record Outcome(int successes, int conflicts) {
    }

    /** Starts all tasks behind one latch and classifies each result as success or {@link LoanConflictException}. */
    private Outcome raceAll(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();

            int successes = 0;
            int conflicts = 0;
            for (Future<Object> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException e) {
                    assertInstanceOf(LoanConflictException.class, e.getCause());
                    conflicts++;
                }
            }
            return new Outcome(successes, conflicts);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Removes loans, then the borrower and copies, in foreign-key order. */
    private void cleanup(User borrower, List<Book> copies) {
        loanRepository.deleteAll(loanRepository.findAll().stream()
                .filter(loan -> loan.getBorrower().getId().equals(borrower.getId())
                        || copies.stream().anyMatch(copy -> copy.getId().equals(loan.getBook().getId())))
                .toList());
        userRepository.deleteById(borrower.getId());
        copies.forEach(copy -> bookRepository.deleteById(copy.getId()));
    }

    /** Creates a valid student with unique test-only identifiers. */
    private User createStudent() {
        int sequence = FIXTURE_SEQUENCE.incrementAndGet();
        String universityId = String.format("8%03d-%04d", sequence % 1000, sequence);
        User borrower = new User();
        borrower.setUniversityId(universityId);
        borrower.setPasswordHash("test-only-hash");
        borrower.setFirstName("Concurrent");
        borrower.setLastName("Borrower");
        borrower.setEmail(universityId + "@example.test");
        borrower.setUserRole(UserRole.STUDENT);
        borrower.setUserCourse(UserCourse.IS);
        return userRepository.saveAndFlush(borrower);
    }

    /** Creates one loanable copy. */
    private Book createBook() {
        return bookRepository.saveAndFlush(new Book("Concurrent " + FIXTURE_SEQUENCE.incrementAndGet(),
                "Test Author", "Test", new BigDecimal("10.00")));
    }
}
