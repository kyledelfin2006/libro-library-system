package unit;

import app.book.exceptions.BookNotFoundException;
import app.book.exceptions.BookValidationException;
import app.book.mapper.BookMapper;
import app.book.service.BookService;
import app.book.entity.Book;
import app.book.dto.BookRequestDTO;
import app.book.repository.BookRepository;
import jakarta.validation.constraints.Positive;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Isolated unit tests for {@link BookService} business rules and repository orchestration.
 *
 * <p>The repository mock and service are created once for this class, while the mock is reset
 * before each test to preserve isolation without repeating Mockito initialization. The suite
 * never starts Spring, Hibernate, or a database. Consequently,
 * assertions about dirty checking verify that the service does not call {@code save}; actual
 * persistence-context flushing remains an integration-test concern.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookServiceTest {

    /** Mocked persistence boundary used to define query results and verify repository calls. */
    private final BookRepository repository = mock(BookRepository.class);

    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();

    /** Service under test, using the real stateless mapper and mocked persistence boundary. */
    private final BookService bookService = new BookService(
            repository, new BookMapper(), validatorFactory.getValidator());

    @AfterAll
    void closeValidatorFactory() {
        validatorFactory.close();
    }

    /** Canonical persisted entity fixture reset before every test. */
    private Book sampleBook;

    /** Canonical valid create/replace request fixture reset before every test. */
    private BookRequestDTO sampleBookRequestDTO;

    /** Stable identifier used by lookup, update, and delete scenarios. */
    private final Long BOOK_ID = 1L;

    /** Expected title shared by the canonical fixtures. */
    private final String TITLE = "Effective Java";

    /** Expected author shared by the canonical fixtures. */
    private final String AUTHOR = "Joshua Bloch";

    /** Expected genre shared by the canonical fixtures. */
    private final String GENRE = "Programming";

    /** Expected positive monetary value shared by the canonical fixtures. */
    private final @Positive(message = "Price must be greater than 0") BigDecimal PRICE = new BigDecimal("45.0");

    /** Resets mock behavior and rebuilds mutable fixtures before each isolated scenario. */
    @BeforeEach
    void setUp() {
        reset(repository);
        sampleBook = new Book(TITLE, AUTHOR, GENRE, PRICE);
        sampleBook.setId(BOOK_ID);

        sampleBookRequestDTO = new BookRequestDTO(TITLE, AUTHOR, GENRE, PRICE);
    }

    // ---------- addBook ----------
    /** Verifies that a valid request is mapped, saved once, and returned as a populated entity. */
    @Test
    void addBook_shouldSaveAndReturnBook() {
        when(repository.save(any(Book.class))).thenReturn(sampleBook);

        Book result = bookService.addBook(sampleBookRequestDTO);

        assertNotNull(result);
        assertEquals(TITLE, result.getTitle());
        assertEquals(AUTHOR, result.getAuthor());
        assertEquals(GENRE, result.getGenre());
        assertEquals(0, PRICE.compareTo(result.getPrice()));
        verify(repository, times(1)).save(any(Book.class));
    }

    /** Verifies service callers cannot bypass entity constraints by skipping controller validation. */
    @Test
    void addBook_whenEntityConstraintsFail_shouldRejectBeforeSave() {
        BookRequestDTO invalid = new BookRequestDTO(" ", "Author", "Genre", BigDecimal.ONE);

        assertThrows(ConstraintViolationException.class, () -> bookService.addBook(invalid));

        verify(repository, never()).save(any());
    }

    // ---------- findBookById ----------
    /** Verifies that an entity returned by the repository is passed back unchanged. */
    @Test
    void findBookById_whenBookExists_shouldReturnBook() {
        when(repository.findById(BOOK_ID)).thenReturn(Optional.of(sampleBook));

        Book result = bookService.findBookById(BOOK_ID);

        assertEquals(sampleBook, result);
        verify(repository, times(1)).findById(BOOK_ID);
    }

    /** Verifies that an empty repository result is translated to {@link BookNotFoundException}. */
    @Test
    void findBookById_whenBookNotFound_shouldThrowBookNotFoundException() {
        when(repository.findById(BOOK_ID)).thenReturn(Optional.empty());

        assertThrows(BookNotFoundException.class, () -> bookService.findBookById(BOOK_ID));
        verify(repository, times(1)).findById(BOOK_ID);
    }

    // ---------- patchBook ----------
    /** Verifies partial replacement of title and price while omitted fields remain unchanged. */
    @Test
    void patchBook_shouldUpdateOnlyProvidedFields() {
        // Given an existing book
        Book existing = new Book("Old Title", "Old Author", "Old Genre", new BigDecimal("10.0"));
        existing.setId(BOOK_ID);
        when(repository.findById(BOOK_ID)).thenReturn(Optional.of(existing));

        // Update only title and price
        BookRequestDTO updates = new BookRequestDTO("New Title", null, null, new BigDecimal("30.0"));

        Book result = bookService.patchBook(BOOK_ID, updates);

        assertEquals("New Title", result.getTitle());
        assertEquals("Old Author", result.getAuthor());  // unchanged
        assertEquals("Old Genre", result.getGenre());    // unchanged
        assertEquals(0, new BigDecimal("30.0").compareTo(result.getPrice()));
        // No save() call expected
        verify(repository, never()).save(any());
    }

    /** Verifies that an author-only PATCH does not modify title, genre, or price. */
    @Test
    void patchBook_shouldUpdateOnlyAuthorWhenOnlyAuthorProvided() {
        Book existing = new Book("Old Title", "Old Author", "Old Genre", new BigDecimal("10.0"));
        existing.setId(BOOK_ID);
        when(repository.findById(BOOK_ID)).thenReturn(Optional.of(existing));

        BookRequestDTO updates = new BookRequestDTO(null, "New Author", null, null);

        Book result = bookService.patchBook(BOOK_ID, updates);

        assertEquals("Old Title", result.getTitle());
        assertEquals("New Author", result.getAuthor());
        assertEquals("Old Genre", result.getGenre());
        assertEquals(0, new BigDecimal("10.0").compareTo(result.getPrice()));
        verify(repository, never()).save(any());
    }

    /** Verifies that a genre-only PATCH does not modify title, author, or price. */
    @Test
    void patchBook_shouldUpdateOnlyGenreWhenOnlyGenreProvided() {
        Book existing = new Book("Old Title", "Old Author", "Old Genre", new BigDecimal("10.0"));
        existing.setId(BOOK_ID);
        when(repository.findById(BOOK_ID)).thenReturn(Optional.of(existing));

        BookRequestDTO updates = new BookRequestDTO(null, null, "New Genre", null);

        Book result = bookService.patchBook(BOOK_ID, updates);

        assertEquals("Old Title", result.getTitle());
        assertEquals("Old Author", result.getAuthor());
        assertEquals("New Genre", result.getGenre());
        assertEquals(0, new BigDecimal("10.0").compareTo(result.getPrice()));
        verify(repository, never()).save(any());
    }

    /** Verifies that blank text and null values are treated as absent PATCH fields. */
    @Test
    void patchBook_whenAllFieldsBlankOrNull_shouldLeaveBookUnchanged() {
        Book existing = new Book("Old Title", "Old Author", "Old Genre", new BigDecimal("10.0"));
        existing.setId(BOOK_ID);
        when(repository.findById(BOOK_ID)).thenReturn(Optional.of(existing));

        // Blank strings should be treated as no update
        BookRequestDTO updates = new BookRequestDTO("   ", "", "  ", null);

        Book result = bookService.patchBook(BOOK_ID, updates);

        assertEquals("Old Title", result.getTitle());
        assertEquals("Old Author", result.getAuthor());
        assertEquals("Old Genre", result.getGenre());
        assertEquals(0, new BigDecimal("10.0").compareTo(result.getPrice()));
        verify(repository, never()).save(any());
    }

    /** Verifies that a null PATCH price preserves the existing monetary value. */
    @Test
    void patchBook_whenPriceIsNull_shouldLeavePriceUnchanged() {
        Book existing = new Book("Old Title", "Old Author", "Old Genre", new BigDecimal("10.0"));
        existing.setId(BOOK_ID);
        when(repository.findById(BOOK_ID)).thenReturn(Optional.of(existing));

        BookRequestDTO updates = new BookRequestDTO("New Title", null, null, null);

        Book result = bookService.patchBook(BOOK_ID, updates);

        assertEquals("New Title", result.getTitle());
        assertEquals(0, new BigDecimal("10.0").compareTo(result.getPrice())); // unchanged
        verify(repository, never()).save(any());
    }

    /** Verifies that a negative PATCH price is rejected before persistence. */
    @Test
    void patchBook_whenPriceIsZeroOrNegative_shouldThrowBookValidationException() {
        when(repository.findById(BOOK_ID)).thenReturn(Optional.of(sampleBook));
        BookRequestDTO updates = new BookRequestDTO(null, null, null, new BigDecimal("-5.0"));

        assertThrows(BookValidationException.class, () -> bookService.patchBook(BOOK_ID, updates));
        verify(repository, never()).save(any());
    }

    /** Verifies that zero is rejected because prices must be strictly positive. */
    @Test
    void patchBook_whenPriceIsExactlyZero_shouldThrowBookValidationException() {
        when(repository.findById(BOOK_ID)).thenReturn(Optional.of(sampleBook));
        BookRequestDTO updates = new BookRequestDTO(null, null, null, BigDecimal.ZERO);

        assertThrows(BookValidationException.class, () -> bookService.patchBook(BOOK_ID, updates));
        verify(repository, never()).save(any());
    }

    /** Verifies that PATCH delegates missing-entity handling to the service lookup contract. */
    @Test
    void patchBook_whenBookNotFound_shouldThrowBookNotFoundException() {
        when(repository.findById(BOOK_ID)).thenReturn(Optional.empty());
        BookRequestDTO updates = new BookRequestDTO("New", null, null, new BigDecimal("20.0"));

        assertThrows(BookNotFoundException.class, () -> bookService.patchBook(BOOK_ID, updates));
        verify(repository, never()).save(any());
    }

    // ---------- replaceBook ----------
    /** Verifies PUT semantics by replacing every mutable field through the mapper. */
    @Test
    void replaceBook_shouldReplaceAllFields() {
        when(repository.findById(BOOK_ID)).thenReturn(Optional.of(sampleBook));
        BookRequestDTO newData = new BookRequestDTO("New Title", "New Author", "New Genre", new BigDecimal("99.99"));

        Book result = bookService.replaceBook(BOOK_ID, newData);

        assertEquals("New Title", result.getTitle());
        assertEquals("New Author", result.getAuthor());
        assertEquals("New Genre", result.getGenre());
        assertEquals(0, new BigDecimal("99.99").compareTo(result.getPrice()));}

    /** Verifies that a null replacement payload is rejected before repository interaction. */
    @Test
    void replaceBook_whenDtoIsNull_shouldThrowBookValidationException() {
        assertThrows(BookValidationException.class, () -> bookService.replaceBook(BOOK_ID, null));
        verify(repository, never()).save(any());
    }

    /** Verifies that PUT requires a non-null title. */
    @Test
    void replaceBook_whenDtoHasNullTitle_shouldThrowBookValidationException() {
        BookRequestDTO invalidDto = new BookRequestDTO(null, "Author", "Genre", new BigDecimal("20.0"));

        assertThrows(BookValidationException.class, () -> bookService.replaceBook(BOOK_ID, invalidDto));
        verify(repository, never()).save(any());
    }

    /** Verifies that PUT rejects titles containing only whitespace. */
    @Test
    void replaceBook_whenDtoHasEmptyTitle_shouldThrowBookValidationException() {
        BookRequestDTO invalidDto = new BookRequestDTO("   ", "Author", "Genre", new BigDecimal("20.0"));

        assertThrows(BookValidationException.class, () -> bookService.replaceBook(BOOK_ID, invalidDto));
        verify(repository, never()).save(any());
    }

    /** Verifies that PUT requires a non-null author. */
    @Test
    void replaceBook_whenDtoHasNullAuthor_shouldThrowBookValidationException() {
        BookRequestDTO invalidDto = new BookRequestDTO("Title", null, "Genre", new BigDecimal("20.0"));

        assertThrows(BookValidationException.class, () -> bookService.replaceBook(BOOK_ID, invalidDto));
        verify(repository, never()).save(any());
    }

    /** Verifies that PUT rejects blank authors. */
    @Test
    void replaceBook_whenDtoHasEmptyAuthor_shouldThrowBookValidationException() {
        BookRequestDTO invalidDto = new BookRequestDTO("Title", "", "Genre", new BigDecimal("20.0"));

        assertThrows(BookValidationException.class, () -> bookService.replaceBook(BOOK_ID, invalidDto));
        verify(repository, never()).save(any());
    }

    /** Verifies that PUT requires a non-null genre. */
    @Test
    void replaceBook_whenDtoHasNullGenre_shouldThrowBookValidationException() {
        BookRequestDTO invalidDto = new BookRequestDTO("Title", "Author", null, new BigDecimal("20.0"));

        assertThrows(BookValidationException.class, () -> bookService.replaceBook(BOOK_ID, invalidDto));
        verify(repository, never()).save(any());
    }

    /** Verifies that PUT rejects genres containing only whitespace. */
    @Test
    void replaceBook_whenDtoHasEmptyGenre_shouldThrowBookValidationException() {
        BookRequestDTO invalidDto = new BookRequestDTO("Title", "Author", "  ", new BigDecimal("20.0"));

        assertThrows(BookValidationException.class, () -> bookService.replaceBook(BOOK_ID, invalidDto));
        verify(repository, never()).save(any());
    }

    /** Verifies that PUT rejects a zero price. */
    @Test
    void replaceBook_whenDtoHasPriceZero_shouldThrowBookValidationException() {
        BookRequestDTO invalidDto = new BookRequestDTO("Title", "Author", "Genre", BigDecimal.ZERO);

        assertThrows(BookValidationException.class, () -> bookService.replaceBook(BOOK_ID, invalidDto));
        verify(repository, never()).save(any());
    }

    /** Verifies that PUT rejects a negative price. */
    @Test
    void replaceBook_whenDtoHasNegativePrice_shouldThrowBookValidationException() {
        BookRequestDTO invalidDto = new BookRequestDTO("Title", "Author", "Genre", new BigDecimal("-10.0"));

        assertThrows(BookValidationException.class, () -> bookService.replaceBook(BOOK_ID, invalidDto));
        verify(repository, never()).save(any());
    }

    /** Verifies that a valid replacement still fails when its target entity does not exist. */
    @Test
    void replaceBook_whenBookNotFound_shouldThrowBookNotFoundException() {
        when(repository.findById(BOOK_ID)).thenReturn(Optional.empty());
        BookRequestDTO dto = new BookRequestDTO("Title", "Author", "Genre", new BigDecimal("20.0"));

        assertThrows(BookNotFoundException.class, () -> bookService.replaceBook(BOOK_ID, dto));
        verify(repository, never()).save(any());
    }

    // ---------- getBooksWithinBudget ----------
    /** Verifies delegation to the inclusive less-than-or-equal budget repository query. */
    @Test
    void getBooksWithinBudget_shouldReturnBooksWithPriceLessThanOrEqual() {
        List<Book> expected = Arrays.asList(sampleBook, new Book("Cheap", "Author", "Genre", new BigDecimal("10.0")));
        when(repository.findByPriceLessThanEqual(new BigDecimal("30.0"))).thenReturn(expected);

        List<Book> result = bookService.getBooksWithinBudget(new BigDecimal("30.0"));

        assertEquals(2, result.size());
        verify(repository, times(1)).findByPriceLessThanEqual(new BigDecimal("30.0"));
    }

    // ---------- searchBooks ----------
    /** Verifies author searches use the case-insensitive contains repository method. */
    @Test
    void searchBooks_byAuthor_shouldReturnMatchingBooks() {
        List<Book> expected = List.of(sampleBook);
        when(repository.findByAuthorContainingIgnoreCase("Bloch")).thenReturn(expected);

        List<Book> result = bookService.searchBooks("author", "Bloch");

        assertEquals(expected, result);
        verify(repository, times(1)).findByAuthorContainingIgnoreCase("Bloch");
    }

    /** Verifies search-type normalization is case-insensitive and trims surrounding whitespace. */
    @Test
    void searchBooks_byAuthor_isCaseAndWhitespaceInsensitiveForType() {
        List<Book> expected = List.of(sampleBook);
        when(repository.findByAuthorContainingIgnoreCase("Bloch")).thenReturn(expected);

        List<Book> result = bookService.searchBooks("  AUTHOR  ", "Bloch");

        assertEquals(expected, result);
        verify(repository, times(1)).findByAuthorContainingIgnoreCase("Bloch");
    }

    /** Verifies title searches use the case-insensitive contains repository method. */
    @Test
    void searchBooks_byTitle_shouldReturnMatchingBooks() {
        List<Book> expected = List.of(sampleBook);
        when(repository.findByTitleContainingIgnoreCase("Effective")).thenReturn(expected);

        List<Book> result = bookService.searchBooks("title", "Effective");

        assertEquals(expected, result);
        verify(repository, times(1)).findByTitleContainingIgnoreCase("Effective");
    }

    /** Verifies genre searches use the case-insensitive contains repository method. */
    @Test
    void searchBooks_byGenre_shouldReturnMatchingBooks() {
        List<Book> expected = List.of(sampleBook);
        when(repository.findByGenreContainingIgnoreCase("Program")).thenReturn(expected);

        List<Book> result = bookService.searchBooks("genre", "Program");

        assertEquals(expected, result);
        verify(repository, times(1)).findByGenreContainingIgnoreCase("Program");
    }

    /** Verifies price text is converted to {@link BigDecimal} and matched exactly. */
    @Test
    void searchBooks_byPrice_shouldReturnBooksWithMatchingPrice() {
        List<Book> expected = List.of(sampleBook);
        // The service uses Long.parseLong, so pass an integer string
        when(repository.findByPrice(new BigDecimal("45"))).thenReturn(expected);

        List<Book> result = bookService.searchBooks("price", "45");

        assertEquals(expected, result);
        verify(repository, times(1)).findByPrice(new BigDecimal("45"));
    }

    /** Verifies malformed price text is rejected without invoking the price repository query. */
    @Test
    void searchBooks_byPrice_withInvalidNumber_shouldThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> bookService.searchBooks("price", "not-a-number"));
        verify(repository, never()).findByPrice(any(BigDecimal.class));
    }

    /** Verifies unsupported search fields are rejected instead of becoming dynamic queries. */
    @Test
    void searchBooks_withInvalidType_shouldThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> bookService.searchBooks("invalid", "value"));
        verify(repository, never()).findByAuthorContainingIgnoreCase(anyString());
    }

    // ---------- getBooksSortedBy ----------
    /** Verifies an allowed field creates an ascending Spring Data {@link Sort}. */
    @Test
    void getBooksSortedBy_validField_shouldReturnSortedList() {
        List<Book> expected = List.of(sampleBook);
        when(repository.findAll(Sort.by("title").ascending())).thenReturn(expected);

        List<Book> result = bookService.getBooksSortedBy("title");

        assertEquals(expected, result);
        verify(repository, times(1)).findAll(Sort.by("title").ascending());
    }

    /** Verifies sort-field normalization before constructing the ascending sort. */
    @Test
    void getBooksSortedBy_validFieldMixedCaseWithWhitespace_shouldReturnSortedList() {
        List<Book> expected = List.of(sampleBook);
        when(repository.findAll(Sort.by("price").ascending())).thenReturn(expected);

        List<Book> result = bookService.getBooksSortedBy("  PRICE  ");

        assertEquals(expected, result);
        verify(repository, times(1)).findAll(Sort.by("price").ascending());
    }

    /** Verifies the sort allowlist rejects unknown entity properties. */
    @Test
    void getBooksSortedBy_invalidField_shouldThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> bookService.getBooksSortedBy("invalidField"));
        verify(repository, never()).findAll(any(Sort.class));
    }

    /** Verifies a null sort field returns the repository's natural unsorted result. */
    @Test
    void getBooksSortedBy_nullField_shouldReturnAllUnsorted() {
        List<Book> expected = List.of(sampleBook);
        when(repository.findAll()).thenReturn(expected);

        List<Book> result = bookService.getBooksSortedBy(null);

        assertEquals(expected, result);
        verify(repository, times(1)).findAll();
        verify(repository, never()).findAll(any(Sort.class));
    }

    /** Verifies a whitespace-only sort field returns the unsorted result. */
    @Test
    void getBooksSortedBy_emptyField_shouldReturnAllUnsorted() {
        List<Book> expected = List.of(sampleBook);
        when(repository.findAll()).thenReturn(expected);

        List<Book> result = bookService.getBooksSortedBy("   ");

        assertEquals(expected, result);
        verify(repository, times(1)).findAll();
        verify(repository, never()).findAll(any(Sort.class));
    }

    // ---------- getTotalLibraryValue ----------
    /** Verifies that a repository aggregate is returned as the total collection value. */
    @Test
    void getTotalLibraryValue_whenBooksExist_shouldReturnSum() {
        when(repository.sumTotalOfPrice()).thenReturn(Optional.of(new BigDecimal("150.0")));

        BigDecimal total = bookService.getTotalLibraryValue();

        assertEquals(0, new BigDecimal("150.0").compareTo(total));
        verify(repository, times(1)).sumTotalOfPrice();
    }

    /** Verifies that an empty SUM result is represented as {@link BigDecimal#ZERO}. */
    @Test
    void getTotalLibraryValue_whenNoBooks_shouldReturnZero() {
        when(repository.sumTotalOfPrice()).thenReturn(Optional.empty());

        BigDecimal total = bookService.getTotalLibraryValue();

        assertEquals(0, BigDecimal.ZERO.compareTo(total));
        verify(repository, times(1)).sumTotalOfPrice();
    }

    // ---------- findMostExpensiveBook ----------
    /** Verifies that the repository's highest-priced entity is returned unchanged. */
    @Test
    void findMostExpensiveBook_shouldReturnBookWithHighestPrice() {
        when(repository.findTopByOrderByPriceDesc()).thenReturn(sampleBook);

        Book result = bookService.findMostExpensiveBook();

        assertEquals(sampleBook, result);
        verify(repository, times(1)).findTopByOrderByPriceDesc();
    }

    /** Verifies that a populated AVG aggregate is returned without numerical distortion. */
    @Test
    void getAveragePrice_whenBooksExist_shouldReturnAverage() {
        when(repository.getAveragePrice()).thenReturn(Optional.of(new BigDecimal("25.50")));
        BigDecimal avg = bookService.getAveragePrice();
        assertEquals(0, new BigDecimal("25.50").compareTo(avg));
    }

    /** Verifies that an empty AVG result is represented as {@link BigDecimal#ZERO}. */
    @Test
    void getAveragePrice_whenNoBooks_shouldReturnZero() {
        when(repository.getAveragePrice()).thenReturn(Optional.empty());
        BigDecimal avg = bookService.getAveragePrice();
        assertEquals(BigDecimal.ZERO, avg);
    }

    /** Verifies that an empty collection has no most-expensive book and returns {@code null}. */
    @Test
    void findMostExpensiveBook_whenNoBooks_shouldReturnNull() {
        when(repository.findTopByOrderByPriceDesc()).thenReturn(null);

        Book result = bookService.findMostExpensiveBook();

        assertNull(result);
        verify(repository, times(1)).findTopByOrderByPriceDesc();
    }
}
