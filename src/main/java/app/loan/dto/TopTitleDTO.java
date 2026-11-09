package app.loan.dto;

/** Checkout count across physical copies sharing one title and author. */
public record TopTitleDTO(String title, String author, long checkouts) {
}
