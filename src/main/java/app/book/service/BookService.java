package app.book.service;

import app.global.exception.InvalidRequestException;
import app.audit.service.AuditService;
import app.book.dto.BookResponseDTO;
import app.book.dto.LibraryStatisticsDTO;
import app.book.exception.BookNotFoundException;
import app.book.exception.BookValidationException;
import app.book.entity.Book;
import app.book.entity.BookCopyStatus;
import app.loan.entity.LoanStatus;
import app.book.dto.BookRequestDTO;
import app.book.dto.BookPatchRequestDTO;
import app.book.mapper.BookMapper;
import app.book.repository.BookRepository;
import app.book.repository.projection.GenreCount;
import app.book.repository.projection.LibraryAggregate;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Application service for the book catalog: retrieval, search, statistics, creation, update, and deletion.
 * <p>
 * The service is the transaction boundary and the only place that touches {@link Book} entities. Every public method
 * accepts request DTOs and returns response DTOs, so an entity never leaves the transaction (open-in-view is off, so
 * mapping after the method returns could hit a closed persistence context).
 * </p>
 * <p>
 * The class is {@code @Transactional(readOnly = true)} by default, which lets Hibernate skip dirty checking for reads.
 * Write methods override it with a plain {@code @Transactional}.
 * </p>
 *
 * @author Kyle Delfin
 * @see BookRepository
 * @see BookMapper
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class BookService {

    private static final Set<String> SORTABLE_BOOK_FIELDS = Set.of("id", "title", "author", "genre", "replacementCost");

    /** Data access for book rows. */
    private final BookRepository repository;
    /** Explicit entity/DTO conversion. */
    private final BookMapper mapper;
    private final Validator validator;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AuditService audit;


    /**
     * Retrieves a paginated list of all books.
     *
     * @param pageable the pagination and sorting information
     * @return a page of books matching the given pageable parameters
     * @throws InvalidRequestException if the sort names a field outside the allowlist
     */
    public Page<BookResponseDTO> getBooks(Pageable pageable) {
        validateBookSort(pageable);
        return mapPage(repository.findAll(pageable));
    }

    /**
     * Filters books by text and optional inclusive replacement-cost bounds before applying a stable page sort.
     *
     * @param title optional title substring
     * @param author optional author substring
     * @param genre optional genre substring
     * @param minReplacementCost optional inclusive lower bound
     * @param maxReplacementCost optional inclusive upper bound
     * @param pageable requested page and allowlisted sort order
     * @return the matching page of physical copies
     * @throws InvalidRequestException if the replacement-cost range or sort order is invalid
     */
    public Page<BookResponseDTO> queryBooks(String title, String author, String genre,
                                            BigDecimal minReplacementCost, BigDecimal maxReplacementCost,
                                            Pageable pageable) {
        return queryBooks(title, author, genre, minReplacementCost, maxReplacementCost, null, null, pageable);
    }

    /** Filters by physical disposition and current availability in addition to existing catalog fields. */
    public Page<BookResponseDTO> queryBooks(String title, String author, String genre,
                                            BigDecimal minReplacementCost, BigDecimal maxReplacementCost,
                                            BookCopyStatus copyStatus, Boolean available, Pageable pageable) {
        return mapPage(queryBookPage(title, author, genre, minReplacementCost, maxReplacementCost,
                copyStatus, available, pageable));
    }

    /** Resolves active loans once for a page, avoiding one loan lookup per book. */
    private Page<BookResponseDTO> mapPage(Page<Book> books) {
        List<Long> ids = books.getContent().stream().map(Book::getId).toList();
        Set<Long> activeIds = ids.isEmpty() ? Set.of() :
                Optional.ofNullable(repository.findActiveBookIds(ids, LoanStatus.ACTIVE)).orElse(Set.of());
        // A lost copy cannot be borrowed even when its loan is terminal.
        return books.map(book -> mapper.toResponseDTO(book,
                book.getCopyStatus() == BookCopyStatus.IN_COLLECTION && !activeIds.contains(book.getId())));
    }

    /** Maps a single copy using its current persisted active-loan state. */
    private BookResponseDTO mapBook(Book book) {
        // Newly constructed copies in isolated service tests have no persistence-assigned ID yet.
        if (book.getId() == null) return mapper.toResponseDTO(book);
        boolean available = book.getCopyStatus() == BookCopyStatus.IN_COLLECTION
                && !Optional.ofNullable(repository.findActiveBookIds(List.of(book.getId()), LoanStatus.ACTIVE))
                .orElse(Set.of()).contains(book.getId());
        return mapper.toResponseDTO(book, available);
    }

    /**
     * Runs the shared filtered query behind every list and search endpoint.
     * <p>
     * Validates the sort against the allowlist and rejects a reversed range. A final {@code id} order keeps page
     * boundaries deterministic when several books share the requested sort value.
     * </p>
     *
     * @return the matching entity page; public callers map it to DTOs before returning
     */
    private Page<Book> queryBookPage(String title, String author, String genre,
                                     BigDecimal minReplacementCost, BigDecimal maxReplacementCost,
                                     BookCopyStatus copyStatus, Boolean available, Pageable pageable) {
        validateBookSort(pageable);
        if (minReplacementCost != null && maxReplacementCost != null
                && minReplacementCost.compareTo(maxReplacementCost) > 0) {
            throw new InvalidRequestException("minReplacementCost must be less than or equal to maxReplacementCost");
        }
        Sort sort = pageable.getSort();
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by("id"));
        }
        Pageable stablePage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        String safeTitle = title == null ? "" : title;
        String safeAuthor = author == null ? "" : author;
        String safeGenre = genre == null ? "" : genre;
        return copyStatus == null && available == null
                ? repository.queryBooks(safeTitle, safeAuthor, safeGenre,
                        minReplacementCost, maxReplacementCost, stablePage)
                : repository.queryBooksFiltered(safeTitle, safeAuthor, safeGenre,
                        minReplacementCost, maxReplacementCost, copyStatus, available,
                        BookCopyStatus.IN_COLLECTION, LoanStatus.ACTIVE, stablePage);
    }

    private void validateBookSort(Pageable pageable) {
        if (pageable.getSort().stream().anyMatch(order -> !SORTABLE_BOOK_FIELDS.contains(order.getProperty()))) {
            throw new InvalidRequestException("Unsupported book sort field. Allowed fields: id, title, author, genre, replacementCost");
        }
    }

    /**
     * Creates a new book from the provided DTO and saves it to the database.
     * Duplicate title/author pairs are intentionally allowed because the model
     * does not yet carry an ISBN or edition identifier; generated book IDs
     * distinguish separate copies or editions.
     *
     * @param input the request DTO containing book data (must be valid)
     * @return the persisted copy, including its generated ID
     * @throws InvalidRequestException if the DTO contains invalid data
     *                                  (validated at controller level)
     */
    @Transactional
    public BookResponseDTO addBook(BookRequestDTO input) {
        if (input == null) {
            throw new BookValidationException("Book data must not be null");
        }
        log.debug("Processing request to add book: {}", input.getTitle());

        // Use the mapper to convert DTO → Entity (keeps construction centralized)
        Book newBook = mapper.toEntity(input);

        // Validate using validator method
        validateEntity(newBook);
        repository.save(newBook);
        if (audit != null) audit.record("BOOK_CREATED", "BOOK", newBook.getId().toString(), null);

        log.info("Successfully added book '{}' to catalogue with ID: {}", newBook.getTitle(), newBook.getId());
        return mapBook(newBook);
    }

    /**
     * Retrieves a book by its unique identifier.
     *
     * @param id the book's ID
     * @return the found copy
     * @throws BookNotFoundException if no book exists with the given ID
     */
    public BookResponseDTO findBookById(Long id) {
        return repository.findById(id)
                .map(this::mapBook)
                .orElseThrow(() -> new BookNotFoundException("Couldn't find book of ID: " + id));
    }

    /**
     * Loads a physical copy under a write lock for a transaction that will change its fields.
     * The lock makes book edits serialize with loan operations that change copy status.
     *
     * @param id identifier of the physical copy to load
     * @return the managed, write-locked copy
     * @throws BookNotFoundException if no copy exists with the given ID
     */
    private Book findBookByIdForUpdate(Long id) {
        // Reuse the same row lock as checkout and lost-loan resolution to avoid stale copy status.
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new BookNotFoundException("Couldn't find book of ID: " + id));
    }

    /**
     * Deletes a book by its ID.
     * <p>
     * Uses a custom JPQL delete query to avoid loading the entity first.
     * </p>
     *
     * @param id the ID of the book to delete
     * @throws BookNotFoundException if no book exists with the given ID
     * @throws org.springframework.dao.DataIntegrityViolationException if loan history references this copy
     */
    @Transactional
    public void deleteBookById(Long id) {
        log.debug("Attempting to delete book with ID: {}", id);

        if (repository.hasLoanHistory(id)) {
            throw new app.global.exception.ConflictException(
                    "Book copy has loan history and must be retained", "RESOURCE_HAS_HISTORY");
        }

        int deletedCount;
        try {
            // The foreign key remains the final guard if a loan is created after the history check.
            deletedCount = repository.deleteBookById(id);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new app.global.exception.ConflictException(
                    "Book copy has loan history and must be retained", "RESOURCE_HAS_HISTORY");
        }

        if (deletedCount == 0) { // If no row is deleted, it means no id matched that book to delete.
            log.warn("Failed delete operation: Book with ID {} not found", id);
            throw new BookNotFoundException("Couldn't find book of ID: " + id);
        }
        if (audit != null) audit.record("BOOK_DELETED", "BOOK", id.toString(), null);
    }

    /**
     * Performs a partial update on an existing book.
     * <p>
     * Only fields that are non-{@code null} and have text (for strings) are updated.
     * The physical-copy row is write-locked before loading so a concurrent lost resolution
     * cannot have its copy status overwritten by a stale managed entity.
     * Replacement-cost updates must be positive; otherwise, an exception is thrown.
     * </p>
     *
     * @param id      the ID of the book to update
     * @param updates the DTO containing the fields to update (others remain unchanged)
     * @return the updated copy (the managed entity is flushed by dirty checking at commit)
     * @throws BookNotFoundException      if the book does not exist
     * @throws BookValidationException if the supplied replacement cost is ≤ 0
     */
    @Transactional
    public BookResponseDTO patchBook(Long id, BookPatchRequestDTO updates) {

        if (updates == null) {
            throw new BookValidationException("Book update data must not be null");
        }

        // 1. Lock and load the copy before applying changes to prevent status lost updates.
        Book existingBook = findBookByIdForUpdate(id);

        // 2. Only set new value if update is available
        if (hasText(updates.title())) {
            existingBook.setTitle(updates.title().trim());
        }
        if (hasText(updates.author())) {
            existingBook.setAuthor(updates.author().trim());
        }
        if (hasText(updates.genre())) {
            existingBook.setGenre(updates.genre().trim());
        }
        // Blank descriptions mean "leave unchanged" in PATCH; full replacement can clear them.
        if (hasText(updates.description())) {
            // Reject overlong input before changing the managed entity so callers get a clear catalog error.
            if (updates.description().length() > 2000) {
                throw new BookValidationException("Description cannot exceed 2000 characters");
            }
            existingBook.setDescription(updates.description().trim());
        }
        if (updates.replacementCost() != null) {
            if (updates.replacementCost().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BookValidationException("Replacement cost must be greater than 0");
            }
            existingBook.setReplacementCost(updates.replacementCost());
        }

        validateEntity(existingBook);
        if (audit != null) audit.record("BOOK_CHANGED", "BOOK", id.toString(), null);
        // No repository.save(): the entity is managed, so Hibernate writes the changes when the transaction commits.
        return mapBook(existingBook);
    }

    /** Returns whether a string is non-null and contains a non-whitespace character. */
    private boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /**
     * Completely replaces an existing book with new data.
     * <p>
     * All editable catalog fields are overwritten with the values from the DTO. The row lock
     * preserves concurrently committed physical-copy status changes such as a lost resolution.
     * </p>
     *
     * @param id      the ID of the book to replace
     * @param updates the DTO containing the complete new data (must be valid)
     * @return the updated copy
     * @throws BookNotFoundException    if the book does not exist
     * @throws BookValidationException if the DTO is {@code null} or contains invalid data
     *                                 (e.g., blank title, negative replacement cost)
     */
    @Transactional
    public BookResponseDTO replaceBook(Long id, BookRequestDTO updates) {
        // 1. Validate the incoming DTO (replace requires a complete, valid payload)
        if (updates == null) {
            throw new BookValidationException("Book data must not be null");
        }
        if (!hasText(updates.getTitle())) {
            throw new BookValidationException("Title must not be blank");
        }
        if (!hasText(updates.getAuthor())) {
            throw new BookValidationException("Author must not be blank");
        }
        if (!hasText(updates.getGenre())) {
            throw new BookValidationException("Genre must not be blank");
        }
        if (updates.getReplacementCost() == null || updates.getReplacementCost().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BookValidationException("Replacement cost must be greater than 0");
        }

        // 2. Lock and fetch the copy so a concurrent lost resolution cannot be overwritten.
        Book existingBook = findBookByIdForUpdate(id);

        // 3. Update entire entity using BookMapper
        mapper.updateBookFromDto(updates, existingBook);

        // 4. Validate entity using existing validator method
        validateEntity(existingBook);
        if (audit != null) audit.record("BOOK_CHANGED", "BOOK", id.toString(), null);

        // 5. Dirty checking persists the managed entity at commit; return its API view.
        return mapBook(existingBook);
    }

    /**
     * Enforces the validation constraints declared on {@link Book} before write reaches the
     * persistence boundary. This protects service callers that do not pass through controller
     * DTO validation and makes entity constraints independent of provider lifecycle callbacks.
     *
     * @param book entity about to be inserted or updated
     * @throws ConstraintViolationException if any entity constraint is violated
     */
    private void validateEntity(Book book) {
        Set<ConstraintViolation<Book>> violations = validator.validate(book);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    /**
     * Pages books whose replacement cost is at or below the specified maximum.
     * Delegates to {@link #queryBooks} so sort validation and the stable id tie-breaker apply.
     *
     * @param maxReplacementCost the maximum replacement amount (inclusive)
     * @param pageable requested page (size is capped by Spring's max-page-size) and allowlisted sort
     * @return a page of copies at or below the specified replacement amount
     * @throws InvalidRequestException if the maximum is missing or the sort is unsupported
     */
    public Page<BookResponseDTO> getBooksAtOrBelowReplacementCost(BigDecimal maxReplacementCost, Pageable pageable) {
        if (maxReplacementCost == null) {
            throw new InvalidRequestException("maxReplacementCost is required");
        }
        return queryBooks(null, null, null, null, maxReplacementCost, pageable);
    }

    /**
     * Pages books whose replacement cost falls within the given range (inclusive).
     *
     * @param minReplacementCost the minimum amount (must be ≤ max)
     * @param maxReplacementCost the maximum amount (must be ≥ min)
     * @param pageable requested page and allowlisted sort
     * @return a page of books in the replacement-cost range
     * @throws InvalidRequestException if a bound is missing or min > max
     */
    public Page<BookResponseDTO> getBooksInReplacementCostRange(BigDecimal minReplacementCost,
                                                                BigDecimal maxReplacementCost, Pageable pageable) {
        if (minReplacementCost == null || maxReplacementCost == null) {
            throw new InvalidRequestException("minReplacementCost and maxReplacementCost are required");
        }
        // queryBooks rejects a reversed range, so the bounds are not compared twice here.
        return queryBooks(null, null, null, minReplacementCost, maxReplacementCost, pageable);
    }

    /**
     * Pages books by a specific field type and value.
     * <p>
     * Supported search types: {@code author}, {@code title}, {@code genre}, {@code replacementCost}.
     * Text types match case-insensitive substrings; replacement cost requires an exact numeric value.
     * </p>
     *
     * @param type  the field to search on (case-insensitive, trimmed)
     * @param value the search term
     * @param pageable requested page and allowlisted sort
     * @return a page of matching books
     * @throws InvalidRequestException if the search type is unsupported or
     *                                  the replacement-cost value is not a valid number
     */
    public Page<BookResponseDTO> searchBooks(String type, String value, Pageable pageable) {
        if (type == null || type.isBlank()) {
            throw new InvalidRequestException("Search type is required");
        }
        if (value == null) {
            throw new InvalidRequestException("Search value is required");
        }

        String formattedType = type.trim().toLowerCase(Locale.ROOT);

        return switch (formattedType) {
            case "author" -> queryBooks(null, value, null, null, null, pageable);
            case "title" -> queryBooks(value, null, null, null, null, pageable);
            case "genre" -> queryBooks(null, null, value, null, null, pageable);
            case "replacementcost" -> {
                BigDecimal replacementCost;
                try {
                    replacementCost = new BigDecimal(value);
                } catch (NumberFormatException e) {
                    throw new InvalidRequestException("Invalid replacement-cost format value: " + value);
                }
                // An exact match is an inclusive range whose bounds are equal.
                yield queryBooks(null, null, null, replacementCost, replacementCost, pageable);
            }
            default -> throw new InvalidRequestException(
                    "Invalid search type: " + type + ". Valid types: author, title, genre, replacementCost");
        };
    }

    /**
     * Retrieves genre distribution counts and converts the internal typed projections
     * into the existing public map response shape.
     *
     * @return a map containing genre names as keys and their corresponding book counts as values
     */
    public Map<String, Long> getGenreDistribution() {
        return repository.getGenres().stream()
                .collect(Collectors.toMap(
                        GenreCount::genre,
                        GenreCount::count
                ));
    }


    /**
     * Pages all books sorted ascending by the specified field.
     * <p>
     * Allowed sort fields are: {@code title}, {@code author}, {@code id}, {@code replacementCost}, {@code genre}.
     * A null or blank field sorts by id. The field, not any sort carried by {@code pageable}, decides the order.
     * </p>
     *
     * @param field the name of the field to sort by (case-insensitive, trimmed)
     * @param pageable supplies the page number and size only
     * @return a page of books sorted accordingly
     * @throws InvalidRequestException if the field is not one of the allowed values
     */
    public Page<BookResponseDTO> getBooksSortedBy(String field, Pageable pageable) {
        String fieldName = "id";
        if (field != null && !field.trim().isEmpty()) {
            // Normalize ordinary fields to lowercase, but keep the JPA camel-case property spelling.
            String normalized = field.trim().toLowerCase(Locale.ROOT);
            fieldName = normalized.equals("replacementcost") ? "replacementCost" : normalized;
        }

        // Validate against the allowlist so a client can never name an arbitrary entity property.
        if (!SORTABLE_BOOK_FIELDS.contains(fieldName)) {
            throw new InvalidRequestException("Invalid sort field: " + field);
        }

        Pageable sortedPage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(fieldName).ascending());
        return queryBooks(null, null, null, null, null, sortedPage);
    }

    /**
     * Returns library statistics with total replacement cost and the copy with the highest
     * replacement cost (converted to {@link BookResponseDTO}).
     * <p>
     * This method combines two repository calls:
     * - One for COUNT and SUM of replacement costs.
     * - One for the copy with the highest replacement cost.
     * </p>
     *
     * @return a fully populated {@link LibraryStatisticsDTO}
     */
    public LibraryStatisticsDTO getLibraryStatistics() {
        LibraryAggregate aggregate = repository.getCountAndTotalReplacementCost();

        Book highestReplacementCost = repository.findTopByOrderByReplacementCostDesc();

        BookResponseDTO highestReplacementCostDTO = (highestReplacementCost != null)
                ? mapBook(highestReplacementCost) : null;

        return new LibraryStatisticsDTO(
                aggregate.totalBooks(),
                aggregate.totalReplacementCost(),
                highestReplacementCostDTO
        );
    }

    /**
     * Calculates the average replacement cost of all copies in the library.
     *
     * @return the average replacement cost as a {@link BigDecimal}, or {@link BigDecimal#ZERO}
     *         if no books exist
     */
    public BigDecimal getAverageReplacementCost() {
        Optional<BigDecimal> averageReplacementCost = repository.getAverageReplacementCost();
        return averageReplacementCost.orElse(BigDecimal.ZERO);
    }

    /**
     * Counts the physical copies in the collection.
     *
     * @return the number of copies; zero when the library is empty
     */
    public Long getBookCount() {
        return repository.count();
    }

}
