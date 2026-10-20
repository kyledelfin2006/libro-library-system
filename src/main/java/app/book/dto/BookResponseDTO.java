package app.book.dto;

import app.book.entity.BookCopyStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Public book representation. Persistence-only fields are omitted.")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookResponseDTO {
    @Schema(description = "Generated book identifier.", example = "1")
    private Long id;
    @Schema(description = "Normalized book title.", example = "1984")
    private String title;
    @Schema(description = "Normalized author name.", example = "George Orwell")
    private String author;
    @Schema(description = "Normalized genre.", example = "Dystopian")
    private String genre;
    @Schema(description = "Optional catalog summary.", example = "A dystopian novel about surveillance and authoritarianism.")
    private String description;
    @Schema(description = "Positive decimal amount used to replace this physical copy.", example = "19.99")
    private BigDecimal replacementCost;
    @Schema(
            description = "Physical-copy disposition. IN_COLLECTION does not mean available when an active loan exists.",
            example = "IN_COLLECTION")
    private BookCopyStatus copyStatus;

    /**
     * Keeps response construction concise for copies without a description.
     *
     * @param id generated book identifier
     * @param title normalized book title
     * @param author normalized author name
     * @param genre normalized genre
     * @param replacementCost positive replacement amount
     */
    public BookResponseDTO(Long id, String title, String author, String genre, BigDecimal replacementCost) {
        this(id, title, author, genre, null, replacementCost, BookCopyStatus.IN_COLLECTION);
    }
}
