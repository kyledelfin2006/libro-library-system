package app.book.dto;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

//This DTO is only used as a response – serialized to JSON, never deserialize from JSON.
//
//It has exactly three fields – all mandatory, no optional/nullable values.
//
//You never need to modify it – it's a read‑only snapshot of library statistics.
//
//No JPA or Spring‑specific behavior – it's a pure data object.


public record LibraryStatisticsDTO(
        @Schema(description = "Number of books in the collection.", example = "6") long totalBooks,
        @Schema(description = "Sum of all book prices.", example = "123.45") BigDecimal totalValue,
        @Schema(description = "Most expensive book, or null when the collection is empty.") BookResponseDTO mostExpensiveBook
) {
}
