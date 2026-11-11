package app.loan.repository.projection;

import java.time.LocalDate;

/** Typed native-query row for one staff dashboard calendar week. */
public interface WeeklyLoanActivityProjection {

    /** Returns the Monday that begins the bucketed week. */
    LocalDate getWeekStart();

    /** Returns the number of checkouts in the week. */
    Long getCheckouts();

    /** Returns the number of returns in the week. */
    Long getReturns();
}
