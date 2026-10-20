package app.book.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Complete book data used when creating or replacing a book. Title, author, genre, and replacement cost are required; description is optional.")
public class BookRequestDTO {

    // @NotBlank ensures the client doesn't pass a null value, or empty strings
    @NotBlank(message = "Title cannot be empty")
    @Size(max = 100, message = "Title cannot exceed 100 characters")
    @Schema(description = "Book title. Surrounding whitespace is trimmed before persistence.", example = "1984")
    private String title;

    @NotBlank(message = "Author cannot be empty")
    @Size(max = 50, message = "Author cannot exceed 50 characters")
    @Schema(description = "Author name. Surrounding whitespace is trimmed before persistence.", example = "George Orwell")
    private String author;

    @NotBlank(message = "Genre cannot be empty")
    @Size(max = 50, message = "Genre cannot exceed 50 characters")
    @Schema(description = "Book genre. Surrounding whitespace is trimmed before persistence.", example = "Dystopian")
    private String genre;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    @Schema(description = "Optional catalog summary. Surrounding whitespace is trimmed; maximum 2,000 characters.", example = "A dystopian novel about surveillance and authoritarianism.")
    private String description;

    @NotNull(message = "Replacement cost is required")
    @Positive(message = "Replacement cost must be greater than 0")
    @Schema(description = "Positive decimal amount used to replace this physical copy.", example = "19.99")
    private BigDecimal replacementCost;

    /** Creates a complete book request without an optional description. */
    public BookRequestDTO(String title, String author, String genre, BigDecimal replacementCost) {
        this(title, author, genre, null, replacementCost);
    }
}
