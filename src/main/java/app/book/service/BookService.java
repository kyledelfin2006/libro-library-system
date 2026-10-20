package app.book.service;

import app.book.dto.BookResponseDTO;
import app.book.dto.LibraryStatisticsDTO;
import app.book.exceptions.BookNotFoundException;
import app.book.exceptions.BookValidationException;
import app.book.entity.Book;
import app.book.dto.BookRequestDTO;
import app.book.dto.BookPatchRequestDTO;
import app.book.mapper.BookMapper;
import app.book.repository.BookRepository;
import app.book.repository.projection.GenreCount;
import app.book.repository.projection.LibraryAggregate;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service layer for managing {@link Book} entities.
 * <p>
 * This class orchestrates business logic for book operations, including
 * retrieval, creation, update, deletion, and search. It acts as a facade
 * between the controller layer and the repository, translating DTOs to entities
 * and vice versa using {@link BookMapper}.
 * </p>
 * <p>All write operations are annotated with {@code @Transactional} to ensure
 * data consistency and enable automatic dirty checking.</p>
 *
 * @author Kyle Delfin
 * @see BookRepository
 * @see BookMapper
 */
@Slf4j
@AllArgsConstructor
@Service
public class BookService {

    private static final Set<String> SORTABLE_BOOK_FIELDS = Set.of("id", "title", "author", "genre", "replacementCost");

    /**
     *  repository the data access object for book entities
     *  mapper the component for converting between DTOs and entities
     */
    private final BookRepository repository;
    private final BookMapper mapper;
    private final Validator validator;


    /**
     * Retrieves a paginated list of all books.
     *
     * @param pageable the pagination and sorting information
     * @return a page of books matching the given pageable parameters
     */
    public Page<Book> getBooks(Pageable pageable) {
        validateBookSort(pageable);
        return repository.findAll(pageable);
    }

    /**
     * Validates optional filters and delegates a stable, database-paged read.
     * A final {@code id} order keeps page boundaries deterministic when books
     * share the requested sort value.
     */
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
     * @throws IllegalArgumentException if the replacement-cost range or sort order is invalid
     */
    @Transactional(readOnly = true)
    public Page<Book> queryBooks(String title, String author, String genre,
                                 BigDecimal minReplacementCost, BigDecimal maxReplacementCost, Pageable pageable) {
        validateBookSort(pageable);
        if (minReplacementCost != null && maxReplacementCost != null
                && minReplacementCost.compareTo(maxReplacementCost) > 0) {
            throw new IllegalArgumentException("minReplacementCost must be less than or equal to maxReplacementCost");
        }
        Sort sort = pageable.getSort();
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by("id"));
        }
        Pageable stablePage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        return repository.queryBooks(title == null ? "" : title,
                author == null ? "" : author,
                genre == null ? "" : genre,
                minReplacementCost, maxReplacementCost, stablePage);
    }

    private void validateBookSort(Pageable pageable) {
        if (pageable.getSort().stream().anyMatch(order -> !SORTABLE_BOOK_FIELDS.contains(order.getProperty()))) {
            throw new IllegalArgumentException("Unsupported book sort field. Allowed fields: id, title, author, genre, replacementCost");
        }
    }

    /**
     * Creates a new book from the provided DTO and saves it to the database.
     * Duplicate title/author pairs are intentionally allowed because the model
     * does not yet carry an ISBN or edition identifier; generated book IDs
     * distinguish separate copies or editions.
     *
     * @param input the request DTO containing book data (must be valid)
     * @return the persisted {@link Book} entity (with generated ID)
     * @throws IllegalArgumentException if the DTO contains invalid data
     *                                  (validated at controller level)
     */
    @Transactional
    public Book addBook(BookRequestDTO input) {
        if (input == null) {
            throw new BookValidationException("Book data must not be null");
        }
        log.debug("Processing request to add book: {}", input.getTitle());

        // Use the mapper to convert DTO → Entity (keeps construction centralized)
        Book newBook = mapper.toEntity(input);

        // Validate using validator method
        validateEntity(newBook);
        repository.save(newBook);

        log.info("Successfully added book '{}' to catalogue with ID: {}", newBook.getTitle(), newBook.getId());
        return newBook;
    }

    /**
     * Retrieves a book by its unique identifier.
     *
     * @param id the book's ID
     * @return the found {@link Book} entity
     * @throws BookNotFoundException if no book exists with the given ID
     */
    public Book findBookById(Long id) {
        return repository.findById(id)
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

        int deletedCount = repository.deleteBookById(id); // Returns the amount of rows deleted

        if (deletedCount == 0) { // If no row is deleted, it means no id matched that book to delete.
            log.warn("Failed delete operation: Book with ID {} not found", id);
            throw new BookNotFoundException("Couldn't find book of ID: " + id);
        }
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
     * @return the updated {@link Book} entity (managed, persisted by dirty checking)
     * @throws BookNotFoundException      if the book does not exist
     * @throws BookValidationException if the supplied replacement cost is ≤ 0
     */
    // Partial updates
    @Transactional
    public Book patchBook(Long id, BookPatchRequestDTO updates) {

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
        return existingBook; // no repository.save() needed for Transactional methods.
    }

    // Checks whether a string has text and is not null
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
     * @return the updated {@link Book} entity (managed)
     * @throws BookNotFoundException    if the book does not exist
     * @throws BookValidationException if the DTO is {@code null} or contains invalid data
     *                                 (e.g., blank title, negative replacement cost)
     */
    @Transactional
    public Book replaceBook(Long id, BookRequestDTO updates) {
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

        // 5. Persist and return managed book entity through dirty checking
        return existingBook;
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
     * Retrieves all books whose replacement cost is at or below the specified maximum.
     *
     * @param maxReplacementCost the maximum replacement amount (inclusive)
     * @return a list of copies at or below the specified replacement amount
     */
    public List<Book> getBooksAtOrBelowReplacementCost(BigDecimal maxReplacementCost) {
        return repository.findByReplacementCostLessThanEqual(maxReplacementCost);
    }

    /**
     * Retrieves all books whose replacement cost falls within the given range (inclusive).
     *
     * @param minReplacementCost the minimum amount (must be ≤ max)
     * @param maxReplacementCost the maximum amount (must be ≥ min)
     * @return a list of books in the replacement-cost range
     * @throws IllegalArgumentException if min > max
     */
    public List<Book> getBooksInReplacementCostRange(BigDecimal minReplacementCost, BigDecimal maxReplacementCost) {
        if (minReplacementCost == null || maxReplacementCost == null) {
            throw new IllegalArgumentException("minReplacementCost and maxReplacementCost are required");
        }
        if (minReplacementCost.compareTo(maxReplacementCost) > 0) {
            throw new IllegalArgumentException("minReplacementCost must be less than or equal to maxReplacementCost");
        }
        return repository.findBooksByReplacementCostBetween(minReplacementCost, maxReplacementCost);
    }

    /**
     * Searches for books by a specific field type and value.
     * <p>
     * Supported search types: {@code author}, {@code title}, {@code genre}, {@code replacementCost}.
     * For replacement cost, the value must be a valid numeric string.
     * </p>
     *
     * @param type  the field to search on (case-insensitive, trimmed)
     * @param value the search term (case-insensitive for text fields)
     * @return a list of matching books
     * @throws IllegalArgumentException if the search type is unsupported or
     *                                  the replacement-cost value is not a valid number
     */
    public List<Book> searchBooks(String type, String value) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Search type is required");
        }
        if (value == null) {
            throw new IllegalArgumentException("Search value is required");
        }

        String formattedType = type.trim().toLowerCase(Locale.ROOT);

        switch (formattedType) {
            case "author":
                return repository.findByAuthorContainingIgnoreCase(value);
            case "title":
                return repository.findByTitleContainingIgnoreCase(value);
            case "genre":
                return repository.findByGenreContainingIgnoreCase(value);
            case "replacementcost":
                try {
                    BigDecimal replacementCost = new BigDecimal(value);
                    return repository.findByReplacementCost(replacementCost);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid replacement-cost format value: " + value);
                }
            default: // Handler for invalid types
                throw new IllegalArgumentException("Invalid search type: " + type + ". Valid types: author, title, genre, replacementCost");
        }
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
     * Retrieves all books sorted by the specified field in ascending order.
     * <p>
     * Allowed sort fields are: {@code title}, {@code author}, {@code id}, {@code replacementCost}, {@code genre}.
     * If the provided field is {@code null} or empty, the list is returned unsorted.
     * </p>
     *
     * @param field the name of the field to sort by (case-insensitive, trimmed)
     * @return a list of books sorted accordingly
     * @throws IllegalArgumentException if the field is not one of the allowed values
     */
    public List<Book> getBooksSortedBy(String field) {
        if (field == null || field.trim().isEmpty()) {
            return repository.findAll();
        }

        // Normalize ordinary fields to lowercase, but keep the JPA camel-case property spelling.
        String normalizedFieldName = field.trim().toLowerCase(Locale.ROOT);
        String fieldName = normalizedFieldName.equals("replacementcost")
                ? "replacementCost" : normalizedFieldName;

        // Validate allowed fields to avoid SQL injection through Sort.by()
        // If not allowed throw exception
        if (!SORTABLE_BOOK_FIELDS.contains(fieldName)) {
            throw new IllegalArgumentException("Invalid sort field: " + field);
        }

        Sort sort = Sort.by(fieldName).ascending();
        return repository.findAll(sort);
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
                ? mapper.toResponseDTO(highestReplacementCost) : null;

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
    public BigDecimal getAverageReplacementCost(){
        Optional<BigDecimal> averageReplacementCost = repository.getAverageReplacementCost();
        return averageReplacementCost.orElse(BigDecimal.ZERO);
    }

    // ---------------------- Individual statistics (used elsewhere) ----------------------

    /**
     * Computes the sum of recorded replacement costs for all copies.
     * <p>
     * This method is primarily used for unit testing.
     * </p>
     *
     * @return the sum of all replacement costs, or {@link BigDecimal#ZERO} if the library is empty
     */
    // Mainly used by Unit Testing
    public BigDecimal getTotalReplacementCost() {
        return repository.sumTotalReplacementCost().orElse(BigDecimal.ZERO);
    }

    /**
     * Finds the copy with the highest replacement cost in the library.
     * <p>
     * This method is primarily used for unit testing.
     * </p>
     *
     * @return the copy with the highest replacement cost, or {@code null} if the library is empty
     */
    // Mainly used by Unit Testing
    public Book findHighestReplacementCostBook() {
        return repository.findTopByOrderByReplacementCostDesc();
    }

    /**
     * Sends the count of books in the collection.
     *
     *
     * @return the amount of books in collection, or {@code null} if the library is empty
     */
    public Long getBookCount(){
        return repository.count();
    }

    /**
     * Checks database reachability without calculating the exact book count.
     *
     * @return {@code true} when the database responds to a lightweight query
     */
    public boolean isDatabaseReachable() {
        return repository.pingDatabase() == 1;
    }
}
