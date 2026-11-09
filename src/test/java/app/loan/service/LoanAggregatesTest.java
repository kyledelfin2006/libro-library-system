package app.loan.service;

import app.book.repository.BookRepository;
import app.loan.mapper.LoanMapper;
import app.loan.repository.LoanRepository;
import app.user.entity.User;
import app.user.entity.enums.UserRole;
import app.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
        when(loans.weeklyActivity(any(), any(), eq(false)))
                .thenReturn(List.<Object[]>of(new Object[]{Date.valueOf("2026-10-12"), 2L, 1L}));

        var result = service.activity("2025-4321", 2);

        assertEquals(LocalDate.parse("2026-10-12"), result.get(0).weekStart());
        assertEquals(2, result.get(0).checkouts());
        assertEquals(0, result.get(1).checkouts());
        verify(loans).weeklyActivity(eq(LocalDate.parse("2026-10-12").atStartOfDay()),
                eq(LocalDate.parse("2026-10-26").atStartOfDay()), eq(false));
        assertThrows(app.global.exception.InvalidRequestException.class,
                () -> service.activity("2025-4321", 53));
    }
}
