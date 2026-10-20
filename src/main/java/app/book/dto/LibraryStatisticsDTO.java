package app.book.dto;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

// This response DTO is a read-only snapshot; the highest-cost book is null for an empty collection.


public record LibraryStatisticsDTO(
        @Schema(description = "Number of books in the collection.", example = "6") long totalBooks,
        @Schema(description = "Sum of recorded replacement costs.", example = "123.45") BigDecimal totalReplacementCost,
        @Schema(description = "Book with the highest replacement cost, or null when the collection is empty.") BookResponseDTO highestReplacementCostBook
) {
}
