package app.book.dto;

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
    @Schema(description = "Positive decimal price for the book.", example = "19.99")
    private BigDecimal price;
}
