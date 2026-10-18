package app.book.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents one physical book copy held by the library.
 * Each copy has its own database row and may have loan history. The copy status
 * determines whether the service may issue it for another loan.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "books")
public class Book {

    /**
     * The unique identifier of the book.
     * Generated automatically using the database identity column
     * (SERIAL / BIG SERIAL in PostgreSQL).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Matches Serial/BIGSERIAL in PostgreSQL
    private Long id;

    /**
     * <p>
     * The {@link NotBlank} validation annotation is used to ensure that the title, author, genre
     * is not {@code null}, not empty, and does not consist solely of whitespace.
     * This prevents empty or meaningless strings from being persisted.
     * </p>
     * The maximum length is 100 characters, enforced by the database column definition.
     */
    @NotBlank(message = "Title cannot be blank")
    @Column(nullable = false, length = 100)
    private String title;

    @NotBlank(message = "Author cannot be blank")
    @Column(nullable = false, length = 50)
    private String author;

    @NotBlank(message = "Genre cannot be blank")
    @Column(nullable = false, length = 50)
    private String genre;

    /**
     * The price of the book.
     * <p>
     * The {@link Positive} validation constraint ensures that the price must be
     * strictly greater than zero. However, by default {@code @Positive} allows
     * {@code null} values (unlike {@code @NotBlank}, which only applies to strings).
     * Since the price is a required field, the {@link NotNull} constraint is
     * added explicitly to enforce that the price is always provided and cannot
     * be {@code null}.
     * </p>
     * <p>
     * At the database level, the column is defined as {@code NOT NULL} with
     * a precision of 10 digits and a scale of 2 decimal places, suitable for
     * storing monetary values.
     * </p>
     */
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than 0")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "copy_status", nullable = false, length = 24)
    private BookCopyStatus copyStatus = BookCopyStatus.IN_COLLECTION;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Creates a new copy that is initially part of the loanable collection.
     *
     * @param title copy title
     * @param author copy author
     * @param genre copy genre
     * @param price recorded book value
     */
    public Book(String title, String author, String genre, BigDecimal price) {
        this.title = title;
        this.author = author;
        this.genre = genre;
        this.price = price;
        this.copyStatus = BookCopyStatus.IN_COLLECTION;
    }

    /**
     * Marks this physical copy as lost so it cannot be loaned again.
     */
    public void markLost() {
        // Copy condition is separate from whether its last loan is active.
        this.copyStatus = BookCopyStatus.LOST;
    }

    /**
     * Sets the creation timestamp immediately before the copy is inserted.
     */
    @PrePersist
    public void prePersist() {
        // Preserve the catalog's existing creation-time behavior; loan events use the Manila Clock.
        this.createdAt = LocalDateTime.now();
    }
}
