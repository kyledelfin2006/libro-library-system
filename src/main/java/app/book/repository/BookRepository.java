package app.book.repository;

import app.book.entity.Book;
import app.book.repository.projection.GenreCount;
import app.book.repository.projection.LibraryAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

/** Persistence operations for physical book copies and collection statistics. */
@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * Loads and write-locks one physical copy during checkout, catalog edits, or loss resolution.
     * Using the same row lock for state-changing paths prevents an update based on an old
     * copy-status value from overwriting a concurrently committed lost status.
     *
     * @param id physical copy identifier
     * @return the locked copy, if it exists
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Book b WHERE b.id = :id")
    Optional<Book> findByIdForUpdate(@Param("id") Long id);

    /**
     * Finds a page of books matching every supplied filter.
     * Text filters are case-insensitive literal substrings; an empty string matches all
     * values for that field. Replacement-cost bounds are inclusive and may be {@code null}.
     * The service validates the bounds and sort fields before calling this method.
     *
     * @param title title substring, or an empty string for no title filter
     * @param author author substring, or an empty string for no author filter
     * @param genre genre substring, or an empty string for no genre filter
     * @param minReplacementCost inclusive minimum amount, or {@code null} for no minimum
     * @param maxReplacementCost inclusive maximum amount, or {@code null} for no maximum
     * @param pageable requested page and validated sort order
     * @return matching books with page metadata and total match count
     */
    @Query("""
            SELECT b FROM Book b
            WHERE LOCATE(LOWER(:title), LOWER(b.title)) > 0
              AND LOCATE(LOWER(:author), LOWER(b.author)) > 0
              AND LOCATE(LOWER(:genre), LOWER(b.genre)) > 0
              AND b.replacementCost >= COALESCE(:minReplacementCost, b.replacementCost)
              AND b.replacementCost <= COALESCE(:maxReplacementCost, b.replacementCost)
            """)
    Page<Book> queryBooks(@Param("title") String title,
                          @Param("author") String author,
                          @Param("genre") String genre,
                          @Param("minReplacementCost") BigDecimal minReplacementCost,
                          @Param("maxReplacementCost") BigDecimal maxReplacementCost,
                          Pageable pageable);

    /**
     * Performs a constant-time database reachability check without scanning the books table.
     *
     * @return {@code 1} when the database accepted the query
     */
    @Query(value = "SELECT 1", nativeQuery = true)
    int pingDatabase();

    /**
     * Finds all books whose title contains the given substring (case-insensitive).
     *
     * @param title the substring to search for in the title (non-null)
     * @return a list of books with a title containing {@code title} (ignoring case),
     *         or an empty list if none found
     */
    List<Book> findByTitleContainingIgnoreCase(String title);

    /**
     * Finds all books whose author contains the given substring (case-insensitive).
     *
     * @param author the substring to search for in the author (non-null)
     * @return a list of books with an author containing {@code author} (ignoring case),
     *         or an empty list if none found
     */
    List<Book> findByAuthorContainingIgnoreCase(String author);

    /**
     * Finds all books whose genre contains the given substring (case-insensitive).
     *
     * @param genre the substring to search for in the genre (non-null)
     * @return a list of books with a genre containing {@code genre} (ignoring case),
     *         or an empty list if none found
     */
    List<Book> findByGenreContainingIgnoreCase(String genre);

    /**
     * Finds all books whose replacement cost is less than or equal to the specified maximum.
     *
     * @param replacementCost the maximum amount (inclusive)
     * @return a list of books whose replacement cost is ≤ {@code replacementCost}
     */
    List<Book> findByReplacementCostLessThanEqual(BigDecimal replacementCost);

    /**
     * Finds all books with the exact given replacement cost.
     *
     * @param replacementCost the exact amount to match
     * @return a list of books whose replacement cost equals {@code replacementCost}
     */
    List<Book> findByReplacementCost(BigDecimal replacementCost);

    /**
     * Finds all books whose replacement cost falls within the given inclusive range.
     *
     * @param minReplacementCost the minimum amount (inclusive)
     * @param maxReplacementCost the maximum amount (inclusive)
     * @return a list of books with a replacement cost between the bounds
     */
    @Query("SELECT b FROM Book b WHERE b.replacementCost BETWEEN :minReplacementCost AND :maxReplacementCost")
    List<Book> findBooksByReplacementCostBetween(@Param("minReplacementCost") BigDecimal minReplacementCost,
                                                 @Param("maxReplacementCost") BigDecimal maxReplacementCost);

    /**
     * Retrieves the book with the highest replacement cost.
     *
     * <p>If multiple books share the same maximum replacement cost, only one is returned
     * (the first encountered by the underlying query).</p>
     *
     * @return the highest replacement-cost book, or {@code null} if no books exist
     */
    Book findTopByOrderByReplacementCostDesc();

    /**
     * Calculates the sum of all recorded replacement costs.
     *
     * @return an {@code Optional} containing the total sum, or {@code Optional.empty()}
     *         if no books exist
     */
   @Query("SELECT SUM(b.replacementCost) FROM Book b")
   Optional<BigDecimal> sumTotalReplacementCost();

    /**
     * Retrieves genre distribution counts as a typed constructor projection.
     *
     * <p>The grouped query returns one immutable {@link GenreCount} for each genre,
     * keeping callers independent of positional array elements and runtime casts.</p>
     *
     * @return a list of typed genre/count projections
     */
   @Query("""
           SELECT new app.book.repository.projection.GenreCount(
               b.genre,
               COUNT(b)
           )
           FROM Book b
           GROUP BY b.genre
           """)
   List<GenreCount> getGenres(); // Used in Genre Distribution


    /**
     * Retrieves the total number of books and the sum of their recorded replacement costs.
     *
     * <p>The aggregate row is mapped to a named projection so callers do not depend on
     * positional {@code Object[]} elements or runtime casts.</p>
     *
     * @return a typed aggregate containing the count and total replacement cost
     */
    @Query("""
            SELECT new app.book.repository.projection.LibraryAggregate(
                COUNT(b),
                COALESCE(SUM(b.replacementCost), 0)
            )
            FROM Book b
            """)
    LibraryAggregate getCountAndTotalReplacementCost(); // Used in Library Statistics

    /**
     * Deletes a book by its ID using a custom JPQL query.
     *
     * <p>This method bypasses the JPA lifecycle and does not load the entity
     * into the persistence context. The {@link Modifying} annotation ensures
     * the query is executed as an update. {@code clearAutomatically = true}
     * clears the persistence context after deletion to prevent stale entity
     * references.</p>
     *
     * @param id the ID of the book to delete
     * @return the number of rows deleted (0 if no book with the given ID was found)
     * @throws org.springframework.dao.DataIntegrityViolationException if a loan references this copy
     */
    @Modifying(clearAutomatically = true) // Essentially it clears the persistence context to avoid stale data
    @Query("DELETE FROM Book b WHERE b.id = :id")
    int deleteBookById(@Param("id") Long id);

    /**
     * Calculates the average replacement cost of all books in the library.
     *
     * @return an {@code Optional} containing the average replacement cost, or
     *         {@code Optional.empty()} if no books exist
     */
    @Query("SELECT AVG(b.replacementCost) FROM Book b")
    Optional<BigDecimal> getAverageReplacementCost();




}
