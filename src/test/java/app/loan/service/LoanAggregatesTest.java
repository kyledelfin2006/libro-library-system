package app.loan.service;

import app.book.repository.BookRepository;
import app.loan.mapper.LoanMapper;
import app.loan.repository.LoanRepository;
import app.loan.repository.projection.TopTitleProjection;
import app.loan.repository.projection.WeeklyLoanActivityProjection;
import app.user.entity.User;
import app.user.entity.enums.UserRole;
import app.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Aggregates use Manila week boundaries, zero fill, and the stored staff role. */
class LoanAggregatesTest {

    @Test
    void activityIncludesEmptyWeeksAndPassesAssistantScope() {
        LoanRepository loans = mock(LoanRepository.class);
        UserRepository users = mock(UserRepository.class);
        User assistant = new User();
        assistant.setUserRole(UserRole.ADMINISTRATOR);
        when(users.findByUniversityId("2025-4321")).thenReturn(Optional.of(assistant));
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T02:30:00Z"), ZoneId.of("Asia/Manila"));
        LoanService service = new LoanService(loans, users, mock(BookRepository.class), new LoanMapper(), clock);
        WeeklyLoanActivityProjection week = mock(WeeklyLoanActivityProjection.class);
        when(week.getWeekStart()).thenReturn(LocalDate.parse("2026-10-12"));
        when(week.getCheckouts()).thenReturn(2L);
        when(week.getReturns()).thenReturn(1L);
        when(loans.weeklyActivity(any(), any(), eq(false))).thenReturn(List.of(week));

        var result = service.activity("2025-4321", 2);

        assertEquals(LocalDate.parse("2026-10-12"), result.get(0).weekStart());
        assertEquals(2, result.get(0).checkouts());
        assertEquals(0, result.get(1).checkouts());
        verify(loans).weeklyActivity(eq(LocalDate.parse("2026-10-12").atStartOfDay()),
                eq(LocalDate.parse("2026-10-26").atStartOfDay()), eq(false));
        assertThrows(app.global.exception.InvalidRequestException.class,
                () -> service.activity("2025-4321", 53));
    }

    @Test
    void topTitlesMapsTypedRowsAndPassesLibrarianScope() {
        LoanRepository loans = mock(LoanRepository.class);
        UserRepository users = mock(UserRepository.class);
        User librarian = new User();
        librarian.setUserRole(UserRole.LIBRARIAN);
        when(users.findByUniversityId("2025-4321")).thenReturn(Optional.of(librarian));
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T02:30:00Z"), ZoneId.of("Asia/Manila"));
        LoanService service = new LoanService(loans, users, mock(BookRepository.class), new LoanMapper(), clock);
        TopTitleProjection title = mock(TopTitleProjection.class);
        when(title.getTitle()).thenReturn("The Library Book");
        when(title.getAuthor()).thenReturn("Susan Orlean");
        when(title.getCheckouts()).thenReturn(4L);
        when(loans.topTitles(any(), any(), eq(true), eq(5))).thenReturn(List.of(title));

        var result = service.topTitles("2025-4321", 90, 5);

        assertEquals("The Library Book", result.get(0).title());
        assertEquals("Susan Orlean", result.get(0).author());
        assertEquals(4, result.get(0).checkouts());
        verify(loans).topTitles(eq(LocalDate.parse("2026-07-21").atStartOfDay()),
                eq(LocalDateTime.parse("2026-10-19T10:30:00")), eq(true), eq(5));
    }
}
