package app.book.repository.projection;

import java.math.BigDecimal;

/**
 * Typed result of the library count and total-replacement-cost aggregate query.
 *
 * <p>This is an internal persistence projection, not the public API response DTO.</p>
 */
public record LibraryAggregate(long totalBooks, BigDecimal totalReplacementCost) {
}
