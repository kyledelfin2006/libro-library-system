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
        @Schema(description = "Replacement price; when supplied it must be greater than zero.", example = "12.99", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        BigDecimal price
) {
}
