package app.book.repository;

import app.book.entity.Book;
import app.book.repository.projection.LibraryAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

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
     * @param author the substring to search for in the title (non-null)
     * @return a list of books with a title containing {@code author} (ignoring case),
     *         or an empty list if none found
     */
    List<Book> findByAuthorContainingIgnoreCase(String author);

    /**
     * Finds all books whose genre contains the given substring (case-insensitive).
     *
     * @param genre the substring to search for in the title (non-null)
     * @return a list of books with a title containing {@code title} (ignoring case),
     *         or an empty list if none found
     */
    List<Book> findByGenreContainingIgnoreCase(String genre);

    /**
     * Finds all books with a price less than or equal to the specified maximum.
     *
     * @param price the maximum price (inclusive)
     * @return a list of books whose price is ≤ {@code price}
     */
    List<Book> findByPriceLessThanEqual(BigDecimal price);

    /**
     * Finds all books with the exact given price.
     *
     * @param price the exact price to match
     * @return a list of books whose price equals {@code price}
     */
    List<Book> findByPrice(BigDecimal price);

    /**
     * Finds all books whose price falls within the given inclusive range.
     *
     * @param minPrice the minimum price (inclusive)
     * @param maxPrice the maximum price (inclusive)
     * @return a list of books with a price between {@code minPrice} and {@code maxPrice}
     */
    @Query("SELECT b FROM Book b WHERE b.price BETWEEN :minPrice AND :maxPrice")
    List<Book> findBooksByPriceBetween(@Param("minPrice") BigDecimal minPrice,
                                       @Param("maxPrice") BigDecimal maxPrice);

    /**
     * Retrieves the book with the highest price.
     *
     * <p>If multiple books share the same maximum price, only one is returned
     * (the first encountered by the underlying query).</p>
     *
     * @return the most expensive book, or {@code null} if no books exist
     */
    Book findTopByOrderByPriceDesc();

    /**
     * Calculates the sum of all book prices in the library.
     *
     * @return an {@code Optional} containing the total sum, or {@code Optional.empty()}
     *         if no books exist
     */
   @Query("SELECT SUM(b.price) FROM Book b")
   Optional<BigDecimal> sumTotalOfPrice();

    /**
     * Retrieves genre distribution counts.
     *
     * <p>The query returns a list of {@code Object[]} where each element contains
     * the genre (as a {@code String}) and the count of books in that genre
     * (as a {@code Long}).</p>
     *
     * @return a list of {@code Object[]} arrays, each with two elements:
     *         [genre, count]
     */
   @Query("SELECT b.genre, COUNT(b) FROM Book b GROUP BY b.genre")
   List<Object[]> getGenres(); // Used in Genre Distribution


    /**
     * Retrieves the total number of books and the total monetary value of all books.
     *
     * <p>The aggregate row is mapped to a named projection so callers do not depend on
     * positional {@code Object[]} elements or runtime casts.</p>
     *
     * @return a typed aggregate containing the count and total value
     */
    @Query("""
            SELECT new app.book.repository.projection.LibraryAggregate(
                COUNT(b),
                COALESCE(SUM(b.price), 0)
            )
            FROM Book b
            """)
    LibraryAggregate getCountAndTotalValue(); // Used in Library Statistics

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
     */
    @Modifying(clearAutomatically = true) // Essentially it clears the persistence context to avoid stale data
    @Query("DELETE FROM Book b WHERE b.id = :id")
    int deleteBookById(@Param("id") Long id);

    /**
     * Calculates the average price of all books in the library.
     *
     * @return an {@code Optional} containing the average price, or
     *         {@code Optional.empty()} if no books exist
     */
    @Query("SELECT AVG(b.price) FROM Book b")
    Optional<BigDecimal> getAveragePrice();




}
