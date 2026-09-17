package app.book.repository.projection;

/**
 * Typed result of the genre distribution aggregate query.
 *
 * <p>This is an internal persistence projection, not a public API response DTO.</p>
 */
public record GenreCount(String genre, long count) {
}
