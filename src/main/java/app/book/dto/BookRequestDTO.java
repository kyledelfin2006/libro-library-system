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

    // Added @NotNull to ensure the client doesn't pass a null price
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than 0")
    @Schema(description = "Positive book price in the system currency.", example = "19.99", minimum = "0.01")
    private BigDecimal price;
}
