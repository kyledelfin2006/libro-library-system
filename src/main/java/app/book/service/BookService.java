package app.book.service;

import app.book.dto.BookResponseDTO;
import app.book.dto.LibraryStatisticsDTO;
import app.book.exceptions.BookNotFoundException;
import app.book.exceptions.BookValidationException;
import app.book.entity.Book;
import app.book.dto.BookRequestDTO;
import app.book.mapper.BookMapper;
import app.book.repository.BookRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
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
        return repository.findAll(pageable);
    }

    /**
     * Creates a new book from the provided DTO and saves it to the database.
     *
     * @param input the request DTO containing book data (must be valid)
     * @return the persisted {@link Book} entity (with generated ID)
     * @throws IllegalArgumentException if the DTO contains invalid data
     *                                  (validated at controller level)
     */
    @Transactional
    public Book addBook(BookRequestDTO input) {
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
     * Deletes a book by its ID.
     * <p>
     * Uses a custom JPQL delete query to avoid loading the entity first.
     * </p>
     *
     * @param id the ID of the book to delete
     * @throws BookNotFoundException if no book exists with the given ID
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
     * Price updates must be positive; otherwise, an exception is thrown.
     * </p>
     *
     * @param id      the ID of the book to update
     * @param updates the DTO containing the fields to update (others remain unchanged)
     * @return the updated {@link Book} entity (managed, persisted by dirty checking)
     * @throws BookNotFoundException      if the book does not exist
     * @throws BookValidationException if the provided price is ≤ 0
     */
    // Partial updates
    @Transactional
    public Book patchBook(Long id, BookRequestDTO updates) {

        // 1. Find Book ID
        Book existingBook = findBookById(id);

        // 2. Only set new value if update is available
        if (hasText(updates.getTitle())) {
            existingBook.setTitle(updates.getTitle().trim());
        }
        if (hasText(updates.getAuthor())) {
            existingBook.setAuthor(updates.getAuthor().trim());
        }
        if (hasText(updates.getGenre())) {
            existingBook.setGenre(updates.getGenre().trim());
        }
        if (updates.getPrice() != null) {
            if (updates.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BookValidationException("Price must be greater than 0");
            }
            existingBook.setPrice(updates.getPrice());
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
     * All fields of the book are overwritten with the values from the DTO.
     * </p>
     *
     * @param id      the ID of the book to replace
     * @param updates the DTO containing the complete new data (must be valid)
     * @return the updated {@link Book} entity (managed)
     * @throws BookNotFoundException    if the book does not exist
     * @throws BookValidationException if the DTO is {@code null} or contains invalid data
     *                                 (e.g., blank title, negative price)
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
        if (updates.getPrice() == null || updates.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BookValidationException("Price must be greater than 0");
        }

        // 2. Fetch the existing book (throws 404 if not found)
        Book existingBook = findBookById(id);

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
     * Retrieves all books whose price is less than or equal to the specified maximum.
     *
     * @param maxPrice the maximum price (inclusive)
     * @return a list of books within the budget
     */
    public List<Book> getBooksWithinBudget(BigDecimal maxPrice) {
        return repository.findByPriceLessThanEqual(maxPrice);
    }

    /**
     * Retrieves all books whose price falls within the given range (inclusive).
     *
     * @param min the minimum price (must be ≤ max)
     * @param max the maximum price (must be ≥ min)
     * @return a list of books in the price range
     * @throws IllegalArgumentException if min > max
     */
    public List<Book> getBooksInPriceRange(BigDecimal min, BigDecimal max) {
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("minPrice must be less than or equal to maxPrice");
        }
        return repository.findBooksByPriceBetween(min, max);
    }

    /**
     * Searches for books by a specific field type and value.
     * <p>
     * Supported search types: {@code author}, {@code title}, {@code genre}, {@code price}.
     * For price, the value must be a valid numeric string.
     * </p>
     *
     * @param type  the field to search on (case-insensitive, trimmed)
     * @param value the search term (case-insensitive for text fields)
     * @return a list of matching books
     * @throws IllegalArgumentException if the search type is unsupported or
     *                                  the price value is not a valid number
     */
    public List<Book> searchBooks(String type, String value) {
        String formattedType = type.trim().toLowerCase();

        switch (formattedType) {
            case "author":
                return repository.findByAuthorContainingIgnoreCase(value);
            case "title":
                return repository.findByTitleContainingIgnoreCase(value);
            case "genre":
                return repository.findByGenreContainingIgnoreCase(value);
            case "price":
                try {
                    BigDecimal price = new BigDecimal(value);
                    return repository.findByPrice(price);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid price format value: " + value);
                }
            default: // Handler for invalid types
                throw new IllegalArgumentException("Invalid search type: " + type + ". Valid types: author, title, genre, price");
        }
    }

    /**
     * Retrieves a list of genre–count pairs from the repository and converts it into a map.
     *
     * <p>The repository method {@code getGenres()} returns a {@code List<Object[]>} where
     * each {@code Object[]} contains exactly two elements: the genre (as a {@code String})
     * and the count of books in that genre (as a {@code Long}).
     *
     * <p>The underlying query is:
     * {@code SELECT b.genre, COUNT(b) FROM Book b GROUP BY b.genre}.
     *
     * <p>This method uses {@link Collectors#toMap} to build a {@code Map<String, Long>}
     * where the key is the genre (converted to {@code String}) and the value is the count
     * (converted to {@code Long}).
     *
     * @return a map containing genre names as keys and their corresponding book counts as values
     * @throws NullPointerException if any genre is {@code null} (consider filtering before collecting)
     */
    public Map<String, Long> getGenreDistribution() {
        return repository.getGenres().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // typecasting to appropriate data type
                        row -> (Long) row[1] // Maps the row in the getGenres() array list
                ));
    }


    /**
     * Retrieves all books sorted by the specified field in ascending order.
     * <p>
     * Allowed sort fields are: {@code title}, {@code author}, {@code id}, {@code price}, {@code genre}.
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

        // Clean the field name
        String fieldName = field.trim().toLowerCase();

        // Validate allowed fields to avoid SQL injection through Sort.by()
        Set<String> allowedFields = Set.of("title", "author", "id", "price", "genre");

        // If not allowed throw exception
        if (!allowedFields.contains(fieldName)) {
            throw new IllegalArgumentException("Invalid sort field: " + field);
        }

        Sort sort = Sort.by(fieldName).ascending();
        return repository.findAll(sort);
    }

    /**
     * Returns library statistics as a DTO containing total books, total value,
     * and the most expensive book (converted to {@link BookResponseDTO}).
     * <p>
     * This method combines two repository calls:
     * - One for COUNT and SUM (using {@code getCountAndTotalValue()})
     * - One for the most expensive book (using {@code findTopByOrderByPriceDesc()})
     * </p>
     *
     * @return a fully populated {@link LibraryStatisticsDTO}
     */
    public LibraryStatisticsDTO getLibraryStatistics() {
        // 1. Get array of references from repository
        Object[] stats = repository.getCountAndTotalValue();

        // 2. Set total books from array
        Long totalBooks = (Long) stats[0];

        // 3. Set total value books from array
        BigDecimal totalValue = (BigDecimal) stats[1];

        // 4. Get most expensive book from repository
        Book mostExpensive = repository.findTopByOrderByPriceDesc();

        // 5. Convert the most expensive Book entity to a BookResponseDTO (or null if none)
        BookResponseDTO mostExpensiveDTO = (mostExpensive != null) ? mapper.toResponseDTO(mostExpensive) : null;

        // 6. Return DTO with DTO from step 5.
        return new LibraryStatisticsDTO(totalBooks, totalValue, mostExpensiveDTO);
    }

    /**
     * Calculates the average price of all books in the library.
     *
     * @return the average price as a {@link BigDecimal}, or {@link BigDecimal#ZERO}
     *         if no books exist
     */
    public BigDecimal getAveragePrice(){
        Optional<BigDecimal> averagePrice = repository.getAveragePrice();
        return averagePrice.orElse(BigDecimal.ZERO);
    }

    // ---------------------- Individual statistics (used elsewhere) ----------------------

    /**
     * Computes the total monetary value of all books in the library.
     * <p>
     * This method is primarily used for unit testing.
     * </p>
     *
     * @return the sum of all book prices, or {@link BigDecimal#ZERO} if the library is empty
     */
    // Mainly used by Unit Testing
    public BigDecimal getTotalLibraryValue() {
        return repository.sumTotalOfPrice().orElse(BigDecimal.ZERO);
    }

    /**
     * Finds the most expensive book in the library.
     * <p>
     * This method is primarily used for unit testing.
     * </p>
     *
     * @return the book with the highest price, or {@code null} if the library is empty
     */
    // Mainly used by Unit Testing
    public Book findMostExpensiveBook() {
        return repository.findTopByOrderByPriceDesc();
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
}
