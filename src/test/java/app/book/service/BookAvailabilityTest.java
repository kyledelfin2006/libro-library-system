package app.book.service;

import app.book.entity.Book;
import app.book.entity.BookCopyStatus;
import app.book.mapper.BookMapper;
import app.book.repository.BookRepository;
import app.loan.entity.LoanStatus;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Checks advisory catalog availability without a query per copy. */
class BookAvailabilityTest {
    @Test
    void mixedPageUsesOneActiveLoanLookupAndLostCopiesStayUnavailable() {
        BookRepository repository = mock(BookRepository.class);
        BookService service = new BookService(repository, new BookMapper(), mock(Validator.class));
        Book free = book(1L);
        Book borrowed = book(2L);
        Book lost = book(3L);
        lost.markLost();
        var page = PageRequest.of(0, 12);
        when(repository.findAll(page)).thenReturn(new PageImpl<>(List.of(free, borrowed, lost)));
        when(repository.findActiveBookIds(List.of(1L, 2L, 3L), LoanStatus.ACTIVE)).thenReturn(Set.of(2L));

        var result = service.getBooks(page).getContent();

        assertTrue(result.get(0).isAvailable());
        assertFalse(result.get(1).isAvailable());
        assertFalse(result.get(2).isAvailable());
        verify(repository, times(1)).findActiveBookIds(anyCollection(), eq(LoanStatus.ACTIVE));
    }

    @Test
    void deletionWithLoanHistoryHasStableConflictCode() {
        BookRepository repository = mock(BookRepository.class);
        BookService service = new BookService(repository, new BookMapper(), mock(Validator.class));
        when(repository.hasLoanHistory(1L)).thenReturn(true);
        var conflict = assertThrows(app.global.exception.ConflictException.class,
                () -> service.deleteBookById(1L));
        assertEquals("RESOURCE_HAS_HISTORY", conflict.getCode());
        verify(repository, never()).deleteBookById(1L);
    }

    private Book book(Long id) {
        Book book = new Book("Example", "Author", "Fiction", BigDecimal.TEN);
        book.setId(id);
        return book;
    }
}
