package unit.book;

import app.book.entity.Book;
import app.book.dto.BookRequestDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link Book} constructor and the Jakarta Validation contract exposed by
 * {@link BookRequestDTO}.
 *
 * <p>This suite has no mocked dependencies. Entity construction is tested as a plain Java
 * operation, while DTO constraints are exercised with a real Jakarta {@link Validator}. No
 * Spring application context, repository, or database is started.</p>
 */
class BookTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    /**
     * Verifies that the convenience constructor copies every supplied value and leaves the
     * generated identifier {@code null} until persistence assigns it.
     */
    @Test
    void testCreateValidBook() {
        Book book = new Book("1984", "Orwell", "Fiction", new BigDecimal("15.99"));

        assertEquals("1984", book.getTitle());
        assertEquals("Orwell", book.getAuthor());
        assertEquals("Fiction", book.getGenre());
        assertEquals(0, new BigDecimal("15.99").compareTo(book.getPrice()));
        // ID is null until the repository saves it and sets it via KeyHolder.
        // We do NOT test it here.
        assertNull(book.getId()); // This is the correct state after construction.
    }

    /**
     * Verifies that blank titles, missing authors, and non-positive prices each produce their
     * documented validation message on an invalid request DTO.
     */
    @Test
    void testBookDtoValidationConstraints() {
        // Create an INVALID DTO (empty title, null author, negative price)
        BookRequestDTO invalidDto = new BookRequestDTO("", null, "Fiction", new BigDecimal("-5.0"));

        Set<ConstraintViolation<BookRequestDTO>> violations = VALIDATOR.validate(invalidDto);

        assertFalse(violations.isEmpty(), "Expected validation errors but got none");

        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Title cannot be empty")),
                "Missing validation error for empty title");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Author cannot be empty")),
                "Missing validation error for null author");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Price must be greater than 0")),
                "Missing validation error for price less than or equal to 0");
    }

    /**
     * Verifies that a complete DTO satisfying every constraint produces no violations.
     */
    @Test
    void testValidBookDtoPassesValidation() {
        BookRequestDTO validDto = new BookRequestDTO("The Hobbit", "J.R.R. Tolkien", "Fantasy", new BigDecimal("12.99"));

        Set<ConstraintViolation<BookRequestDTO>> violations = VALIDATOR.validate(validDto);

        assertTrue(violations.isEmpty(), "Expected zero validation errors for a valid DTO, but got: " + violations);
    }

    /** Verifies the persistence model's own constraints are executable, not documentation only. */
    @Test
    void invalidBookEntityFailsValidation() {
        Book invalidBook = new Book(" ", "Author", "Genre", BigDecimal.ZERO);

        Set<ConstraintViolation<Book>> violations = VALIDATOR.validate(invalidBook);

        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("Title cannot be blank")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("Price must be greater than 0")));
    }

    @Test
    void prePersistSetsCreatedAt() {
        Book book = new Book("The Hobbit", "J.R.R. Tolkien", "Fantasy", new BigDecimal("12.99"));

        assertNull(book.getCreatedAt());

        book.prePersist();

        assertNotNull(book.getCreatedAt());
        assertFalse(book.getCreatedAt().isAfter(LocalDateTime.now()));
    }

}
