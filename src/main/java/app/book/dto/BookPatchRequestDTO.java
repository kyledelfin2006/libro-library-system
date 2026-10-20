package app.book.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** Request shape for partial book updates; null and omitted values are not changed. */
@Schema(description = "Fields to update on a book. Omitted or null values are unchanged; blank text values are also ignored.")
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookPatchRequestDTO(
        @Schema(description = "Replacement title. Surrounding whitespace is trimmed.", example = "Animal Farm", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String title,
        @Schema(description = "Replacement author. Surrounding whitespace is trimmed.", example = "George Orwell", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String author,
        @Schema(description = "Replacement genre. Surrounding whitespace is trimmed.", example = "Political Satire", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String genre,
        @Schema(description = "Optional catalog summary. Surrounding whitespace is trimmed; blank values are ignored and the maximum is 2,000 characters.", example = "A dystopian novel about surveillance and authoritarianism.", maxLength = 2000, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String description,
        @Schema(description = "Replacement amount for this physical copy; when supplied it must be greater than zero.", example = "12.99", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        BigDecimal replacementCost
) {

    /** Creates a partial update without changing the optional description. */
    public BookPatchRequestDTO(String title, String author, String genre, BigDecimal replacementCost) {
        this(title, author, genre, null, replacementCost);
    }
}
