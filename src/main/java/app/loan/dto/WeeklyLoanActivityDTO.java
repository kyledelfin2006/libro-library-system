package app.loan.dto;

import java.time.LocalDate;

/** A Manila calendar week, beginning Monday, with checkout and return counts. */
public record WeeklyLoanActivityDTO(LocalDate weekStart, long checkouts, long returns) {
}
