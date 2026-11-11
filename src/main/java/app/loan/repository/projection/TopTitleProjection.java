package app.loan.repository.projection;

/** Typed native-query row for a grouped title and author checkout count. */
public interface TopTitleProjection {

    /** Returns the title shared by the grouped physical copies. */
    String getTitle();

    /** Returns the author shared by the grouped physical copies. */
    String getAuthor();

    /** Returns the number of checkouts in the requested time window. */
    Long getCheckouts();
}
