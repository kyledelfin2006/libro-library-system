package app.loan.repository;

import app.LibraryApplication;
import app.book.entity.BookCopyStatus;
import app.book.repository.BookRepository;
import app.loan.entity.LoanStatus;
import app.support.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

/** PostgreSQL checks for availability predicates and staff-scoped chart grouping. */
@ActiveProfiles("test")
@SpringBootTest(classes = LibraryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(PostgresTestConfig.class)
@ResourceLock("postgres")
class LoanReportingIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired BookRepository books;
    @Autowired LoanRepository loans;

    /** Exercises Sunday/Monday buckets, title-copy grouping, and active/lost availability. */
    @Test
    @Transactional
    void aggregatesAndCatalogFiltersRespectStoredStatesAndRoles() {
        long student = user("2099-9881", "STUDENT");
        long assistant = user("2099-9882", "ADMINISTRATOR");
        long first = book("IN_COLLECTION");
        long second = book("IN_COLLECTION");
        long third = book("IN_COLLECTION");
        book("LOST");
        LocalDateTime sunday = LocalDateTime.of(2026, 10, 18, 23, 59);
        LocalDateTime monday = LocalDateTime.of(2026, 10, 19, 0, 0);
        loan(student, first, sunday, null);
        loan(student, second, monday, monday.plusHours(1));
        loan(assistant, third, monday, null);

        var available = books.queryBooksFiltered("Reporting Probe", "", "", null, null,
                null, true, BookCopyStatus.IN_COLLECTION, LoanStatus.ACTIVE, PageRequest.of(0, 12));
        var unavailable = books.queryBooksFiltered("Reporting Probe", "", "", null, null,
                null, false, BookCopyStatus.IN_COLLECTION, LoanStatus.ACTIVE, PageRequest.of(0, 12));
        assertEquals(1, available.getTotalElements());
        assertEquals(3, unavailable.getTotalElements());

        var assistantWeeks = loans.weeklyActivity(LocalDateTime.of(2026, 10, 12, 0, 0),
                LocalDateTime.of(2026, 10, 26, 0, 0), false);
        assertEquals(2, assistantWeeks.size());
        assertEquals(1, ((Number) assistantWeeks.get(0)[1]).longValue());
        assertEquals(1, ((Number) assistantWeeks.get(1)[1]).longValue());
        assertEquals(1, ((Number) assistantWeeks.get(1)[2]).longValue());
        var titles = loans.topTitles(LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 10, 26, 0, 0), false, 5);
        assertEquals(2, ((Number) titles.get(0)[2]).longValue());
        var librarianTitles = loans.topTitles(LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 10, 26, 0, 0), true, 5);
        assertEquals(3, ((Number) librarianTitles.get(0)[2]).longValue());
    }

    /** Inserts synthetic account data for a transaction-scoped reporting fixture. */
    private long user(String id, String role) {
        return jdbc.queryForObject("""
                INSERT INTO users (university_id, password_hash, first_name, last_name, email,
                                   user_role, student_course)
                VALUES (?, 'test-only-hash', 'Report', 'Probe', ?, ?, ?) RETURNING id
                """, Long.class, id, id + "@example.test", role, "STUDENT".equals(role) ? "IS" : null);
    }

    /** Inserts one copy of the same synthetic title with a selected disposition. */
    private long book(String status) {
        return jdbc.queryForObject("""
                INSERT INTO books (title, author, genre, replacement_cost, copy_status)
                VALUES ('Reporting Probe', 'Synthetic Author', 'Fiction', 10.00, ?) RETURNING id
                """, Long.class, status);
    }

    /** Inserts one history row while preserving the loan table's state checks. */
    private void loan(long user, long book, LocalDateTime borrowed, LocalDateTime returned) {
        jdbc.update("""
                INSERT INTO loans (user_id, book_id, borrowed_at, due_at, status, returned_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, user, book, borrowed, borrowed.plusDays(14),
                returned == null ? "ACTIVE" : "RETURNED", returned);
    }
}
