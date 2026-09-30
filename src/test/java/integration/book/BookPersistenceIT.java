package integration.book;

import app.LibraryApplication;
import app.book.dto.BookPatchRequestDTO;
import app.book.dto.BookRequestDTO;
import app.book.entity.Book;
import app.book.repository.BookRepository;
import app.book.repository.projection.GenreCount;
import app.book.repository.projection.LibraryAggregate;
import app.book.service.BookService;
import integration.PostgresTestConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void applicationStartupAppliesMigrationsAndValidatesSchema() {
        assertEquals(2, flyway.info().applied().length);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'books'",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'users'",
                Integer.class));
    }

    @Test
    @Transactional
    void repositoryQueriesAndTypedProjectionsWorkOnPostgres() {
        bookRepository.saveAllAndFlush(List.of(
                new Book("Animal Farm", "George Orwell", "Satire", new BigDecimal("12.00")),
                new Book("1984", "George Orwell", "Dystopian", new BigDecimal("18.00")),
                new Book("The Hobbit", "J. R. R. Tolkien", "Fantasy", new BigDecimal("24.00"))
        ));

        assertEquals(2, bookRepository.findByAuthorContainingIgnoreCase("ORWELL").size());
        assertEquals(2, bookRepository.findBooksByPriceBetween(
                new BigDecimal("12.00"), new BigDecimal("18.00")).size());

        LibraryAggregate aggregate = bookRepository.getCountAndTotalValue();
        assertEquals(3L, aggregate.totalBooks());
        assertEquals(0, new BigDecimal("54.00").compareTo(aggregate.totalValue()));

        List<GenreCount> genres = bookRepository.getGenres();
        assertEquals(3, genres.size());
        assertTrue(genres.stream().anyMatch(genre -> genre.genre().equals("Satire") && genre.count() == 1));
    }

    @Test
    void patchAndPutPersistManagedChangesAtTransactionCommit() {
        Book saved = bookRepository.saveAndFlush(
                new Book("Old title", "Old author", "Old genre", new BigDecimal("10.00")));
        try {
            bookService.patchBook(saved.getId(), new BookPatchRequestDTO(
                    "Patched title", null, null, new BigDecimal("11.00")));

            Book patched = bookRepository.findById(saved.getId()).orElseThrow();
            assertEquals("Patched title", patched.getTitle());
            assertEquals("Old author", patched.getAuthor());
            assertEquals(0, new BigDecimal("11.00").compareTo(patched.getPrice()));

            bookService.replaceBook(saved.getId(), new BookRequestDTO(
                    "Replacement title", "Replacement author", "New genre", new BigDecimal("15.00")));

            Book replaced = bookRepository.findById(saved.getId()).orElseThrow();
            assertEquals("Replacement title", replaced.getTitle());
            assertEquals("Replacement author", replaced.getAuthor());
            assertEquals("New genre", replaced.getGenre());
            assertEquals(0, new BigDecimal("15.00").compareTo(replaced.getPrice()));
        } finally {
            bookRepository.deleteById(saved.getId());
        }
    }
}
