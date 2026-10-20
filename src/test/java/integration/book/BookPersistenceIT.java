package integration.book;

import app.LibraryApplication;
import app.book.dto.BookPatchRequestDTO;
import app.book.dto.BookRequestDTO;
import app.book.entity.Book;
import app.book.entity.BookCopyStatus;
import app.book.repository.BookRepository;
import app.book.repository.projection.GenreCount;
import app.book.repository.projection.LibraryAggregate;
import app.book.service.BookService;
import app.loan.entity.Loan;
import app.loan.entity.LoanStatus;
import app.loan.repository.LoanRepository;
import app.loan.service.LoanService;
import app.user.entity.User;
import app.user.entity.enums.UserCourse;
import app.user.entity.enums.UserRole;
import app.user.repository.UserRepository;
import integration.PostgresTestConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PostgreSQL integration coverage for Flyway, repository queries, and service transactions. */
@SpringBootTest(classes = LibraryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(PostgresTestConfig.class)
@ResourceLock("postgres")
class BookPersistenceIT {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookService bookService;

    @Autowired
    private LoanService loanService;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void applicationStartupAppliesMigrationsAndValidatesSchema() {
        assertEquals(5, flyway.info().applied().length);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'books'",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'users'",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'loans'",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' " +
                        "AND table_name = 'books' AND column_name = 'copy_status'",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' " +
                        "AND table_name = 'books' AND column_name = 'replacement_cost'",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' " +
                        "AND table_name = 'books' AND column_name = 'description'",
                Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' " +
                        "AND table_name = 'books' AND column_name = 'price'",
                Integer.class));
    }

    /** Verifies V5 upgrades an existing V4 schema without changing stored replacement amounts. */
    @Test
    void v4UpgradeRenamesReplacementAmountAndAddsEmptyDescription() {
        String schemaName = "book_v5_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var dataSource = java.util.Objects.requireNonNull(jdbcTemplate.getDataSource());
        Map<String, String> placeholders = flyway.getConfiguration().getPlaceholders();
        Flyway v4Flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .target(MigrationVersion.fromVersion("4"))
                .placeholders(placeholders)
                .load();
        Flyway latestFlyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .placeholders(placeholders)
                .load();

        try {
            // Build a separate pre-V5 schema and seed the old column before applying the rename.
            v4Flyway.migrate();
            jdbcTemplate.update("INSERT INTO " + schemaName + ".books (title, author, genre, price) VALUES (?, ?, ?, ?)",
                    "Existing copy", "Test author", "Fiction", new BigDecimal("19.99"));
            latestFlyway.migrate();

            BigDecimal replacementCost = jdbcTemplate.queryForObject(
                    "SELECT replacement_cost FROM " + schemaName + ".books WHERE title = ?",
                    BigDecimal.class, "Existing copy");
            String description = jdbcTemplate.queryForObject(
                    "SELECT description FROM " + schemaName + ".books WHERE title = ?",
                    String.class, "Existing copy");

            assertEquals(0, new BigDecimal("19.99").compareTo(replacementCost));
            assertNull(description);
        } finally {
            // Remove only this test's isolated schema after both migration paths finish.
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
    }

    @Test
    @Transactional
    void repositoryQueriesAndTypedProjectionsWorkOnPostgres() {
        bookRepository.saveAllAndFlush(List.of(
                new Book("Animal Farm", "George Orwell", "Satire", "A political allegory.", new BigDecimal("12.00")),
                new Book("1984", "George Orwell", "Dystopian", new BigDecimal("18.00")),
                new Book("The Hobbit", "J. R. R. Tolkien", "Fantasy", new BigDecimal("24.00"))
        ));

        assertEquals(2, bookRepository.findByAuthorContainingIgnoreCase("ORWELL").size());
        assertEquals(2, bookRepository.findBooksByReplacementCostBetween(
                new BigDecimal("12.00"), new BigDecimal("18.00")).size());

        Book described = bookRepository.findByTitleContainingIgnoreCase("Animal Farm").getFirst();
        assertEquals("A political allegory.", described.getDescription());
        assertEquals(0, new BigDecimal("12.00").compareTo(described.getReplacementCost()));

        LibraryAggregate aggregate = bookRepository.getCountAndTotalReplacementCost();
        assertEquals(3L, aggregate.totalBooks());
        assertEquals(0, new BigDecimal("54.00").compareTo(aggregate.totalReplacementCost()));

        List<GenreCount> genres = bookRepository.getGenres();
        assertEquals(3, genres.size());
        assertTrue(genres.stream().anyMatch(genre -> genre.genre().equals("Satire") && genre.count() == 1));
    }

    @Test
    @Transactional
    void paginatedQueryCombinesFiltersAndCountsMatchingRows() {
        bookRepository.saveAllAndFlush(List.of(
                new Book("Animal Farm", "George Orwell", "Satire", new BigDecimal("12.00")),
                new Book("Farm Stories", "George Orwell", "Satire", new BigDecimal("18.00")),
                new Book("Other Farm", "Other Author", "Satire", new BigDecimal("18.00")),
                new Book("1984", "George Orwell", "Dystopian", new BigDecimal("24.00"))
        ));

        Page<Book> first = bookService.queryBooks("farm", "ORWELL", "satire",
                new BigDecimal("12.00"), new BigDecimal("18.00"),
                PageRequest.of(0, 1, Sort.by("replacementCost")));
        Page<Book> second = bookService.queryBooks("farm", "ORWELL", "satire",
                new BigDecimal("12.00"), new BigDecimal("18.00"),
                PageRequest.of(1, 1, Sort.by("replacementCost")));

        assertEquals(2, first.getTotalElements());
        assertEquals(2, first.getTotalPages());
        assertEquals("Animal Farm", first.getContent().getFirst().getTitle());
        assertEquals("Farm Stories", second.getContent().getFirst().getTitle());
        assertEquals(1, bookService.queryBooks(null, null, null, new BigDecimal("24.00"), null,
                PageRequest.of(0, 12)).getTotalElements());
        assertEquals(3, bookService.queryBooks(null, null, null, null, new BigDecimal("18.00"),
                PageRequest.of(0, 12)).getTotalElements());
        assertEquals(4, bookService.queryBooks(null, null, null, null, null,
                PageRequest.of(0, 12)).getTotalElements());
    }

    @Test
    void patchAndPutPersistManagedChangesAtTransactionCommit() {
        Book saved = bookRepository.saveAndFlush(
                new Book("Old title", "Old author", "Old genre", new BigDecimal("10.00")));
        try {
            bookService.patchBook(saved.getId(), new BookPatchRequestDTO(
                    "Patched title", null, null, "Patched summary", new BigDecimal("11.00")));

            Book patched = bookRepository.findById(saved.getId()).orElseThrow();
            assertEquals("Patched title", patched.getTitle());
            assertEquals("Old author", patched.getAuthor());
            assertEquals("Patched summary", patched.getDescription());
            assertEquals(0, new BigDecimal("11.00").compareTo(patched.getReplacementCost()));

            bookService.replaceBook(saved.getId(), new BookRequestDTO(
                    "Replacement title", "Replacement author", "New genre", "Replacement summary", new BigDecimal("15.00")));

            Book replaced = bookRepository.findById(saved.getId()).orElseThrow();
            assertEquals("Replacement title", replaced.getTitle());
            assertEquals("Replacement author", replaced.getAuthor());
            assertEquals("New genre", replaced.getGenre());
            assertEquals("Replacement summary", replaced.getDescription());
            assertEquals(0, new BigDecimal("15.00").compareTo(replaced.getReplacementCost()));
        } finally {
            bookRepository.deleteById(saved.getId());
        }
    }

    /**
     * Verifies a concurrent catalog edit cannot restore a copy's pre-loss status.
     *
     * @throws Exception if a concurrent transaction fails or exceeds its bounded wait
     */
    @Test
    void patchWaitsForLostResolutionAndPreservesLostCopyStatus() throws Exception {
        // Prepare one student borrower, one library assistant, one copy, and one active loan.
        int uniqueSuffix = Math.floorMod((int) System.nanoTime(), 10_000);
        String borrowerId = String.format("9%03d-%04d", uniqueSuffix / 10, uniqueSuffix);
        String staffId = String.format("8%03d-%04d", uniqueSuffix / 10, uniqueSuffix);
        User borrower = new User();
        borrower.setUniversityId(borrowerId);
        borrower.setPasswordHash("test-only-hash");
        borrower.setFirstName("Loan");
        borrower.setLastName("Borrower");
        borrower.setEmail(borrowerId + "@example.test");
        borrower.setUserRole(UserRole.STUDENT);
        borrower.setUserCourse(UserCourse.IS);
        userRepository.saveAndFlush(borrower);

        User staff = new User();
        staff.setUniversityId(staffId);
        staff.setPasswordHash("test-only-hash");
        staff.setFirstName("Library");
        staff.setLastName("Assistant");
        staff.setEmail(staffId + "@example.test");
        staff.setUserRole(UserRole.ADMINISTRATOR);
        userRepository.saveAndFlush(staff);

        Book copy = bookRepository.saveAndFlush(
                new Book("Race title", "Race author", "Test", new BigDecimal("10.00")));
        LocalDateTime borrowedAt = LocalDateTime.now().minusDays(1);
        Loan loan = loanRepository.saveAndFlush(new Loan(borrower, copy, borrowedAt, borrowedAt.plusDays(14)));

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CountDownLatch lostResolutionHoldingLock = new CountDownLatch(1);
        CountDownLatch releaseLostResolution = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        String editApplicationName = "book-edit-lost-race-test";

        try {
            // Keep the loss transaction open after its service operation has locked and changed the copy.
            Future<?> lossResolution = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                loanService.markLost(staffId, loan.getId());
                lostResolutionHoldingLock.countDown();
                try {
                    if (!releaseLostResolution.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release lost-resolution transaction");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Lost-resolution transaction was interrupted", exception);
                }
            }));

            assertTrue(lostResolutionHoldingLock.await(5, TimeUnit.SECONDS),
                    "Lost resolution should reach the point where it holds the copy row lock");

            // Identify the edit connection in pg_stat_activity so the test can wait for the real row-lock wait.
            Future<?> catalogEdit = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.queryForObject(
                        "SELECT set_config('application_name', ?, true)",
                        String.class,
                        editApplicationName);
                bookService.patchBook(copy.getId(), new BookPatchRequestDTO(
                        "Edited while lost", null, null, null));
            }));

            assertTrue(awaitPostgresLockWait(editApplicationName),
                    "The catalog edit should wait for the lost-resolution row lock");

            // Releasing the first transaction allows the edit to read the committed LOST state.
            releaseLostResolution.countDown();
            lossResolution.get(10, TimeUnit.SECONDS);
            catalogEdit.get(10, TimeUnit.SECONDS);

            Book updatedCopy = bookRepository.findById(copy.getId()).orElseThrow();
            Loan updatedLoan = loanRepository.findById(loan.getId()).orElseThrow();
            assertEquals("Edited while lost", updatedCopy.getTitle());
            assertEquals(BookCopyStatus.LOST, updatedCopy.getCopyStatus());
            assertEquals(LoanStatus.LOST, updatedLoan.getStatus());
        } finally {
            // Always unblock the worker before waiting for it and removing its foreign-key fixtures.
            releaseLostResolution.countDown();
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            loanRepository.deleteById(loan.getId());
            userRepository.deleteById(borrower.getId());
            userRepository.deleteById(staff.getId());
            bookRepository.deleteById(copy.getId());
        }
    }

    /**
     * Waits until PostgreSQL reports the named test connection blocked by a database lock.
     *
     * @param applicationName PostgreSQL application name assigned to the concurrent catalog edit
     * @return true if a lock wait was observed before the timeout
     */
    private boolean awaitPostgresLockWait(String applicationName) {
        // Bound the wait so a broken lock path fails the test instead of hanging the suite.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_stat_activity " +
                            "WHERE application_name = ? AND wait_event_type = 'Lock')",
                    Boolean.class,
                    applicationName);
            if (Boolean.TRUE.equals(waiting)) {
                return true;
            }
            try {
                // Poll briefly so the database, rather than an arbitrary sleep, determines progress.
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
