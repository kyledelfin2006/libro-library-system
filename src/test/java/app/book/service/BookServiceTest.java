package app.book.service;

import app.book.exception.BookNotFoundException;
import app.book.exception.BookValidationException;
import app.book.dto.BookResponseDTO;
import app.book.mapper.BookMapper;
import app.global.exception.InvalidRequestException;
import app.audit.entity.AuditAction;
import app.audit.entity.AuditEntityType;
import app.audit.service.AuditService;
import app.book.entity.Book;
import app.book.dto.BookRequestDTO;
import app.book.dto.BookPatchRequestDTO;
import app.book.repository.BookRepository;
import app.book.repository.projection.GenreCount;
import app.book.repository.projection.LibraryAggregate;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

    /** Audit collaborator; the service requires it, so tests supply a mock and can verify recorded events. */
    private final AuditService audit = mock(AuditService.class);

    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();

    /** Service under test, using the real stateless mapper and mocked persistence boundary. */
    private final BookService bookService = new BookService(
            repository, new BookMapper(), validatorFactory.getValidator(), audit);

    @AfterAll
    void closeValidatorFactory() {
        validatorFactory.close();
    }

    /** Canonical persisted entity fixture reset before every test. */
    private Book sampleBook;

    /** Canonical valid create/replace request fixture reset before every test. */
    private BookRequestDTO sampleBookRequestDTO;

    private static final Long BOOK_ID = 1L;
    private static final String TITLE = "Effective Java";
    private static final String AUTHOR = "Joshua Bloch";
    private static final String GENRE = "Programming";
    private static final BigDecimal REPLACEMENT_COST = new BigDecimal("45.0");

    /** Resets mock behavior and rebuilds mutable fixtures before each isolated scenario. */
    @BeforeEach
    void setUp() {
        reset(repository);
        reset(audit);
        sampleBook = new Book(TITLE, AUTHOR, GENRE, REPLACEMENT_COST);
        sampleBook.setId(BOOK_ID);

        sampleBookRequestDTO = new BookRequestDTO(TITLE, AUTHOR, GENRE, REPLACEMENT_COST);
    }

    @Test
    void queryBooks_combinesFiltersAndAddsStableIdSort() {
        Pageable requested = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "replacementCost"));
        when(repository.queryBooks(eq("JAVA"), eq("Bloch"), eq("Programming"),
                eq(new BigDecimal("10")), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleBook)));

        bookService.queryBooks("JAVA", "Bloch", "Programming", new BigDecimal("10"), null, null, null, requested);

        var page = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repository).queryBooks(eq("JAVA"), eq("Bloch"), eq("Programming"),
                eq(new BigDecimal("10")), isNull(), page.capture());
        assertEquals(1, page.getValue().getPageNumber());
        assertEquals(2, page.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, page.getValue().getSort().getOrderFor("replacementCost").getDirection());
        assertEquals(Sort.Direction.ASC, page.getValue().getSort().getOrderFor("id").getDirection());
    }

    @Test
    void queryBooks_rejectsInvalidRangeAndSortBeforeRepositoryAccess() {
        Pageable allowed = PageRequest.of(0, 12, Sort.by("id"));
        assertThrows(InvalidRequestException.class,
                () -> bookService.queryBooks(null, null, null, BigDecimal.TEN, BigDecimal.ONE, null, null, allowed));
        assertThrows(InvalidRequestException.class,
                () -> bookService.queryBooks(null, null, null, null, null, null, null,
                        PageRequest.of(0, 12, Sort.by("createdAt"))));
        verifyNoInteractions(repository);
    }

    // ---------- addBook ----------
    /** Verifies that a valid request is mapped, saved once, and returned as a populated response DTO. */
    @Test
    void addBook_shouldSaveAndReturnBook() {
        when(repository.save(any(Book.class))).thenAnswer(assignGeneratedId());

        BookResponseDTO result = bookService.addBook(sampleBookRequestDTO);

        assertNotNull(result);
        assertEquals(TITLE, result.getTitle());
        assertEquals(AUTHOR, result.getAuthor());
        assertEquals(GENRE, result.getGenre());
        assertEquals(0, REPLACEMENT_COST.compareTo(result.getReplacementCost()));
        verify(repository, times(1)).save(any(Book.class));
        verify(audit).record(AuditAction.BOOK_CREATED, AuditEntityType.BOOK, BOOK_ID.toString(), null);
    }

    /** Verifies a rejected create leaves no audit event behind. */
    @Test
    void addBook_whenInputMissing_shouldNotRecordAuditEvent() {
        assertThrows(InvalidRequestException.class, () -> bookService.addBook(null));
        verifyNoInteractions(audit);
    }

    /** Verifies create requests are normalized before the entity reaches persistence. */
    @Test
    void addBook_shouldTrimTextFieldsBeforeSaving() {
        when(repository.save(any(Book.class))).thenAnswer(assignGeneratedId());

        BookResponseDTO result = bookService.addBook(new BookRequestDTO(
                "  Effective Java  ",
                " Joshua Bloch ",
                " Programming ",
                " A guide to Java best practices. ",
                REPLACEMENT_COST
        ));

        assertEquals(TITLE, result.getTitle());
        assertEquals(AUTHOR, result.getAuthor());
        assertEquals(GENRE, result.getGenre());
        assertEquals("A guide to Java best practices.", result.getDescription());
    }

    /** Verifies service callers cannot bypass entity constraints by skipping controller validation. */
    @Test
    void addBook_whenEntityConstraintsFail_shouldRejectBeforeSave() {
        BookRequestDTO invalid = new BookRequestDTO(" ", "Author", "Genre", BigDecimal.ONE);

        assertThrows(ConstraintViolationException.class, () -> bookService.addBook(invalid));

        verify(repository, never()).save(any());
    }

    // ---------- findBookById ----------
    /** Verifies that a found entity is returned as its response DTO. */
    @Test
    void findBookById_whenBookExists_shouldReturnBook() {
        when(repository.findById(BOOK_ID)).thenReturn(Optional.of(sampleBook));

        BookResponseDTO result = bookService.findBookById(BOOK_ID);

        // The service maps inside its transaction; the caller only ever sees the API view.
        assertEquals(BOOK_ID, result.getId());
        assertEquals(TITLE, result.getTitle());
        verify(repository, times(1)).findById(BOOK_ID);
    }

    /** Verifies that an empty repository result is translated to {@link BookNotFoundException}. */
    @Test
    void findBookById_whenBookNotFound_shouldThrowBookNotFoundException() {
        when(repository.findById(BOOK_ID)).thenReturn(Optional.empty());

        assertThrows(BookNotFoundException.class, () -> bookService.findBookById(BOOK_ID));
        verify(repository, times(1)).findById(BOOK_ID);
    }

    // ---------- deleteBookById ----------
    /** Verifies that a matching repository delete is treated as a successful deletion. */
    @Test
    void deleteBookById_whenBookExists_shouldDeleteBook() {
        when(repository.deleteBookById(BOOK_ID)).thenReturn(1);

        assertDoesNotThrow(() -> bookService.deleteBookById(BOOK_ID));

        verify(repository, times(1)).deleteBookById(BOOK_ID);
    }

    /** Verifies that deleting a missing book is translated to {@link BookNotFoundException}. */
    @Test
    void deleteBookById_whenBookNotFound_shouldThrowBookNotFoundException() {
        when(repository.deleteBookById(BOOK_ID)).thenReturn(0);

        assertThrows(BookNotFoundException.class, () -> bookService.deleteBookById(BOOK_ID));

        verify(repository, times(1)).deleteBookById(BOOK_ID);
    }

    // ---------- patchBook ----------
    /** Verifies partial replacement of title and replacementCost while omitted fields remain unchanged. */
    @Test
    void patchBook_shouldUpdateOnlyProvidedFields() {
        // Given an existing book
        Book existing = new Book("Old Title", "Old Author", "Old Genre", new BigDecimal("10.0"));
        existing.setId(BOOK_ID);
        when(repository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(existing));

        // Update only title and replacementCost
        BookPatchRequestDTO updates = new BookPatchRequestDTO("New Title", null, null, new BigDecimal("30.0"));

        BookResponseDTO result = bookService.patchBook(BOOK_ID, updates);

        assertEquals("New Title", result.getTitle());
        assertEquals("Old Author", result.getAuthor());  // unchanged
        assertEquals("Old Genre", result.getGenre());    // unchanged
        assertEquals(0, new BigDecimal("30.0").compareTo(result.getReplacementCost()));
        verify(repository).findByIdForUpdate(BOOK_ID);
        verify(repository, never()).findById(BOOK_ID);
        // No save() call expected
        verify(repository, never()).save(any());
    }

    /** Verifies that an author-only PATCH does not modify title, genre, or replacementCost. */
    @Test
    void patchBook_shouldUpdateOnlyAuthorWhenOnlyAuthorProvided() {
        Book existing = new Book("Old Title", "Old Author", "Old Genre", new BigDecimal("10.0"));
        existing.setId(BOOK_ID);
        when(repository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(existing));

        BookPatchRequestDTO updates = new BookPatchRequestDTO(null, "New Author", null, null);

        BookResponseDTO result = bookService.patchBook(BOOK_ID, updates);

        assertEquals("Old Title", result.getTitle());
        assertEquals("New Author", result.getAuthor());
        assertEquals("Old Genre", result.getGenre());
        assertEquals(0, new BigDecimal("10.0").compareTo(result.getReplacementCost()));
        verify(repository, never()).save(any());
    }

    /** Verifies that a genre-only PATCH does not modify title, author, or replacementCost. */
    @Test
    void patchBook_shouldUpdateOnlyGenreWhenOnlyGenreProvided() {
        Book existing = new Book("Old Title", "Old Author", "Old Genre", new BigDecimal("10.0"));
        existing.setId(BOOK_ID);
        when(repository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(existing));

        BookPatchRequestDTO updates = new BookPatchRequestDTO(null, null, "New Genre", null);

        BookResponseDTO result = bookService.patchBook(BOOK_ID, updates);

        assertEquals("Old Title", result.getTitle());
        assertEquals("Old Author", result.getAuthor());
        assertEquals("New Genre", result.getGenre());
        assertEquals(0, new BigDecimal("10.0").compareTo(result.getReplacementCost()));
        verify(repository, never()).save(any());
    }

    /** Verifies that blank text and null values are treated as absent PATCH fields. */
    @Test
    void patchBook_whenAllFieldsBlankOrNull_shouldLeaveBookUnchanged() {
        Book existing = new Book("Old Title", "Old Author", "Old Genre", new BigDecimal("10.0"));
        existing.setId(BOOK_ID);
        when(repository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(existing));

        // Blank strings should be treated as no update
        BookPatchRequestDTO updates = new BookPatchRequestDTO("   ", "", "  ", null);

        BookResponseDTO result = bookService.patchBook(BOOK_ID, updates);

        assertEquals("Old Title", result.getTitle());
        assertEquals("Old Author", result.getAuthor());
        assertEquals("Old Genre", result.getGenre());
        assertEquals(0, new BigDecimal("10.0").compareTo(result.getReplacementCost()));
        verify(repository, never()).save(any());
    }

    /** Verifies PATCH trims a supplied description and retains it on the managed copy. */
    @Test
    void patchBook_trimsDescription() {
        Book existing = new Book("Old Title", "Old Author", "Old Genre", "Old summary", new BigDecimal("10.0"));
        existing.setId(BOOK_ID);
        when(repository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(existing));
        BookPatchRequestDTO updates = new BookPatchRequestDTO(null, null, null, "  New summary  ", null);

        BookResponseDTO result = bookService.patchBook(BOOK_ID, updates);

        assertEquals("New summary", result.getDescription());
        verify(repository, never()).save(any());
    }

    /** Verifies PATCH rejects oversized descriptions before persistence. */
    @Test
    void patchBook_rejectsDescriptionOverTwoThousandCharacters() {
        when(repository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(sampleBook));
        BookPatchRequestDTO updates = new BookPatchRequestDTO(null, null, null, "x".repeat(2001), null);

        assertThrows(BookValidationException.class, () -> bookService.patchBook(BOOK_ID, updates));
        verify(repository, never()).save(any());
    }

    /** Verifies that a null PATCH replacementCost preserves the existing monetary value. */
    @Test
    void patchBook_whenReplacementCostIsNull_shouldLeaveReplacementCostUnchanged() {
        Book existing = new Book("Old Title", "Old Author", "Old Genre", new BigDecimal("10.0"));
        existing.setId(BOOK_ID);
        when(repository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(existing));

        BookPatchRequestDTO updates = new BookPatchRequestDTO("New Title", null, null, null);

        BookResponseDTO result = bookService.patchBook(BOOK_ID, updates);

        assertEquals("New Title", result.getTitle());
        assertEquals(0, new BigDecimal("10.0").compareTo(result.getReplacementCost())); // unchanged
        verify(repository, never()).save(any());
    }

    /** Verifies that a negative PATCH replacementCost is rejected before persistence. */
    @Test
    void patchBook_whenReplacementCostIsZeroOrNegative_shouldThrowBookValidationException() {
        when(repository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(sampleBook));
        BookPatchRequestDTO updates = new BookPatchRequestDTO(null, null, null, new BigDecimal("-5.0"));

        assertThrows(BookValidationException.class, () -> bookService.patchBook(BOOK_ID, updates));
        verify(repository, never()).save(any());
    }

    /** Verifies that zero is rejected because replacement costs must be strictly positive. */
    @Test
    void patchBook_whenReplacementCostIsExactlyZero_shouldThrowBookValidationException() {
        when(repository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(sampleBook));
        BookPatchRequestDTO updates = new BookPatchRequestDTO(null, null, null, BigDecimal.ZERO);

        assertThrows(BookValidationException.class, () -> bookService.patchBook(BOOK_ID, updates));
        verify(repository, never()).save(any());
    }

    /** Verifies that PATCH delegates missing-entity handling to the service lookup contract. */
    @Test
    void patchBook_whenBookNotFound_shouldThrowBookNotFoundException() {
        when(repository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.empty());
        BookPatchRequestDTO updates = new BookPatchRequestDTO("New", null, null, new BigDecimal("20.0"));

        assertThrows(BookNotFoundException.class, () -> bookService.patchBook(BOOK_ID, updates));
        verify(repository, never()).save(any());
    }

    // ---------- replaceBook ----------
    /** Verifies PUT semantics by replacing every mutable field through the mapper. */
    @Test
    void replaceBook_shouldReplaceAllFields() {
        when(repository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(sampleBook));
        BookRequestDTO newData = new BookRequestDTO("New Title", "New Author", "New Genre",
                "New catalog summary", new BigDecimal("99.99"));

        BookResponseDTO result = bookService.replaceBook(BOOK_ID, newData);

        assertEquals("New Title", result.getTitle());
        assertEquals("New Author", result.getAuthor());
        assertEquals("New Genre", result.getGenre());
        assertEquals("New catalog summary", result.getDescription());
        assertEquals(0, new BigDecimal("99.99").compareTo(result.getReplacementCost()));
        verify(repository).findByIdForUpdate(BOOK_ID);
        verify(repository, never()).findById(BOOK_ID);
    }

    /** Verifies PUT normalization is consistent with POST and PATCH. */
    @Test
    void replaceBook_shouldTrimTextFieldsBeforeDirtyChecking() {
        when(repository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(sampleBook));

        BookResponseDTO result = bookService.replaceBook(
                BOOK_ID,
                new BookRequestDTO(" New Title ", " New Author ", " New Genre ", " New summary ", REPLACEMENT_COST)
        );

        assertEquals("New Title", result.getTitle());
        assertEquals("New Author", result.getAuthor());
        assertEquals("New Genre", result.getGenre());
        assertEquals("New summary", result.getDescription());
        verify(repository, never()).save(any());
    }

    /** Verifies PUT can clear the optional description by replacing it with null. */
    @Test
    void replaceBook_clearsOptionalDescription() {
        Book existing = new Book("Title", "Author", "Genre", "Old summary", REPLACEMENT_COST);
        existing.setId(BOOK_ID);
        when(repository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(existing));
        BookRequestDTO replacement = new BookRequestDTO("Title", "Author", "Genre", null, REPLACEMENT_COST);

        BookResponseDTO result = bookService.replaceBook(BOOK_ID, replacement);

        assertNull(result.getDescription());
    }

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

    /** Verifies that PUT rejects a zero replacementCost. */
    @Test
    void replaceBook_whenDtoHasReplacementCostZero_shouldThrowBookValidationException() {
        BookRequestDTO invalidDto = new BookRequestDTO("Title", "Author", "Genre", BigDecimal.ZERO);

        assertThrows(BookValidationException.class, () -> bookService.replaceBook(BOOK_ID, invalidDto));
        verify(repository, never()).save(any());
    }

    /** Verifies that PUT rejects a negative replacementCost. */
    @Test
    void replaceBook_whenDtoHasNegativeReplacementCost_shouldThrowBookValidationException() {
        BookRequestDTO invalidDto = new BookRequestDTO("Title", "Author", "Genre", new BigDecimal("-10.0"));

        assertThrows(BookValidationException.class, () -> bookService.replaceBook(BOOK_ID, invalidDto));
        verify(repository, never()).save(any());
    }

    /** Verifies that a valid replacement still fails when its target entity does not exist. */
    @Test
    void replaceBook_whenBookNotFound_shouldThrowBookNotFoundException() {
        when(repository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.empty());
        BookRequestDTO dto = new BookRequestDTO("Title", "Author", "Genre", new BigDecimal("20.0"));

        assertThrows(BookNotFoundException.class, () -> bookService.replaceBook(BOOK_ID, dto));
        verify(repository, never()).save(any());
    }

    // ---------- paginated list reads (all delegate to repository.queryBooks) ----------
    private static final Pageable FIRST_PAGE = PageRequest.of(0, 12, Sort.by("id"));

    /** Stubs the shared paged query for the given text filters and bounds, accepting any stable page. */
    private void stubQuery(String title, String author, String genre, BigDecimal min, BigDecimal max, Page<Book> result) {
        when(repository.queryBooks(eq(title), eq(author), eq(genre), eq(min), eq(max), any(Pageable.class)))
                .thenReturn(result);
    }

    /** Verifies an upper-bound-only filter and a two-sided range both reach the shared query with inclusive bounds. */
    @Test
    void queryBooks_withReplacementCostBounds_shouldForwardBoundsInclusively() {
        Page<Book> expected = new PageImpl<>(List.of(sampleBook));
        stubQuery("", "", "", null, new BigDecimal("30.0"), expected);
        stubQuery("", "", "", BigDecimal.TEN, new BigDecimal("30"), expected);

        assertMappedSample(bookService.queryBooks(null, null, null, null, new BigDecimal("30.0"), null, null, FIRST_PAGE));
        assertMappedSample(bookService.queryBooks(null, null, null, BigDecimal.TEN, new BigDecimal("30"), null, null, FIRST_PAGE));
    }

    /** Verifies reversed replacementCost ranges are rejected before query execution. */
    @Test
    void queryBooks_whenMinimumExceedsMaximum_shouldRejectBeforeQuerying() {
        assertThrows(InvalidRequestException.class, () -> bookService.queryBooks(
                null, null, null, new BigDecimal("30.0"), new BigDecimal("10.0"), null, null, FIRST_PAGE));
        verifyNoInteractions(repository);
    }

    // ---------- getGenreDistribution ----------
    /** Verifies typed genre projections are converted into the public count map. */
    @Test
    void getGenreDistribution_whenGenresExist_shouldMapGenreNamesToCounts() {
        when(repository.getGenres()).thenReturn(List.of(
                new GenreCount("Fiction", 3),
                new GenreCount("Fantasy", 2),
                new GenreCount("Dystopian", 1)));

        assertEquals(Map.of("Fiction", 3L, "Fantasy", 2L, "Dystopian", 1L),
                bookService.getGenreDistribution());
        verify(repository).getGenres();
    }

    /** Verifies an empty repository result produces an empty public distribution map. */
    @Test
    void getGenreDistribution_whenNoGenresExist_shouldReturnEmptyMap() {
        when(repository.getGenres()).thenReturn(List.of());

        assertTrue(bookService.getGenreDistribution().isEmpty());
        verify(repository).getGenres();
    }

    // ---------- getLibraryStatistics ----------
    /** Verifies that the typed aggregate is mapped into the public statistics DTO. */
    @Test
    void getLibraryStatistics_whenBooksExist_shouldReturnTypedAggregateAndHighestReplacementCostBook() {
        LibraryAggregate aggregate = new LibraryAggregate(1L, new BigDecimal("45.00"));
        when(repository.getCountAndTotalReplacementCost()).thenReturn(aggregate);
        when(repository.findTopByOrderByReplacementCostDesc()).thenReturn(sampleBook);

        var result = bookService.getLibraryStatistics();

        assertEquals(1L, result.totalBooks());
        assertEquals(0, new BigDecimal("45.00").compareTo(result.totalReplacementCost()));
        assertNotNull(result.highestReplacementCostBook());
        assertEquals(TITLE, result.highestReplacementCostBook().getTitle());
        verify(repository).getCountAndTotalReplacementCost();
        verify(repository).findTopByOrderByReplacementCostDesc();
    }

    /** Verifies that an empty collection preserves zero totals and no most-expensive book. */
    @Test
    void getLibraryStatistics_whenNoBooks_shouldReturnZeroAndNullHighestReplacementCostBook() {
        when(repository.getCountAndTotalReplacementCost()).thenReturn(new LibraryAggregate(0L, BigDecimal.ZERO));
        when(repository.findTopByOrderByReplacementCostDesc()).thenReturn(null);

        var result = bookService.getLibraryStatistics();

        assertEquals(0L, result.totalBooks());
        assertEquals(BigDecimal.ZERO, result.totalReplacementCost());
        assertNull(result.highestReplacementCostBook());
    }

    /** Mimics the persistence provider, which assigns the generated id to the instance passed to save. */
    private static org.mockito.stubbing.Answer<Book> assignGeneratedId() {
        return invocation -> {
            Book saved = invocation.getArgument(0);
            saved.setId(BOOK_ID);
            return saved;
        };
    }

    /** Asserts a page holds exactly the sample copy, mapped to its response DTO inside the service. */
    private static void assertMappedSample(Page<BookResponseDTO> page) {
        assertEquals(1, page.getContent().size());
        assertEquals(BOOK_ID, page.getContent().getFirst().getId());
        assertEquals(TITLE, page.getContent().getFirst().getTitle());
    }
}
