package unit.loan;

import app.book.entity.Book;
import app.book.entity.BookCopyStatus;
import app.book.repository.BookRepository;
import app.loan.dto.LoanResponseDTO;
import app.loan.entity.Loan;
import app.loan.entity.LoanStatus;
import app.loan.exception.LoanConflictException;
import app.loan.exception.LoanNotFoundException;
import app.loan.mapper.LoanMapper;
import app.loan.repository.LoanRepository;
import app.loan.service.LoanService;
import app.user.entity.User;
import app.user.entity.enums.UserRole;
import app.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for loan rules, ownership boundaries, state changes, and history paging. */
@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    private static final String BORROWER_ID = "2025-4321";
    private static final String STAFF_ID = "2025-8765";
    private static final Long BOOK_ID = 42L;
    private static final Long LOAN_ID = 81L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 10, 30);
    private static final Clock LIBRARY_CLOCK = Clock.fixed(
            Instant.parse("2026-10-19T02:30:00Z"), ZoneId.of("Asia/Manila"));

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookRepository bookRepository;

    private LoanService loanService;

    /** Builds the service with a real mapper and fixed library time for deterministic assertions. */
    @BeforeEach
    void setUp() {
        loanService = new LoanService(
                loanRepository, userRepository, bookRepository, new LoanMapper(), LIBRARY_CLOCK);
    }

    /** Verifies a student checkout creates an active 14-day loan from the authenticated identity. */
    @Test
    void borrow_createsFourteenDayActiveLoanForStudent() {
        User borrower = user(UserRole.STUDENT, BORROWER_ID);
        Book copy = book();
        stubAvailableBorrow(borrower, copy);
        when(loanRepository.saveAndFlush(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoanResponseDTO response = loanService.borrow(BORROWER_ID, BOOK_ID);

        assertEquals(BORROWER_ID, response.borrowerUniversityId());
        assertEquals(BOOK_ID, response.bookId());
        assertEquals(NOW, response.borrowedAt());
        assertEquals(NOW.plusDays(14), response.dueAt());
        assertEquals(LoanStatus.ACTIVE, response.status());
        assertFalse(response.overdue());
        verify(userRepository).findByUniversityIdForUpdate(BORROWER_ID);
        verify(bookRepository).findByIdForUpdate(BOOK_ID);
    }

    /** Verifies faculty can use the same patron checkout path as students. */
    @Test
    void borrow_allowsFaculty() {
        User faculty = user(UserRole.FACULTY, BORROWER_ID);
        stubAvailableBorrow(faculty, book());
        when(loanRepository.saveAndFlush(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoanResponseDTO response = loanService.borrow(BORROWER_ID, BOOK_ID);

        assertEquals(UserRole.FACULTY, faculty.getUserRole());
        assertEquals(LoanStatus.ACTIVE, response.status());
    }

    /** Verifies library staff cannot borrow even when a copy is available. */
    @Test
    void borrow_rejectsLibraryAssistant() {
        when(userRepository.findByUniversityIdForUpdate(STAFF_ID))
                .thenReturn(Optional.of(user(UserRole.ADMINISTRATOR, STAFF_ID)));

        assertThrows(AccessDeniedException.class, () -> loanService.borrow(STAFF_ID, BOOK_ID));

        verifyNoInteractions(bookRepository, loanRepository);
    }

    /** Verifies a copy explicitly marked lost cannot be checked out again. */
    @Test
    void borrow_rejectsLostCopy() {
        User borrower = user(UserRole.STUDENT, BORROWER_ID);
        Book copy = book();
        copy.markLost();
        stubBorrowerAndCopy(borrower, copy);

        assertThrows(LoanConflictException.class, () -> loanService.borrow(BORROWER_ID, BOOK_ID));

        verify(loanRepository, never()).saveAndFlush(any());
    }

    /** Verifies an existing active loan makes the physical copy unavailable. */
    @Test
    void borrow_rejectsAlreadyBorrowedCopy() {
        User borrower = user(UserRole.STUDENT, BORROWER_ID);
        stubBorrowerAndCopy(borrower, book());
        when(loanRepository.existsByBook_IdAndStatus(BOOK_ID, LoanStatus.LOST)).thenReturn(false);
        when(loanRepository.existsByBook_IdAndStatus(BOOK_ID, LoanStatus.ACTIVE)).thenReturn(true);

        assertThrows(LoanConflictException.class, () -> loanService.borrow(BORROWER_ID, BOOK_ID));

        verify(loanRepository, never()).saveAndFlush(any());
    }

    /** Verifies a borrower with an overdue active loan cannot start another loan. */
    @Test
    void borrow_rejectsBorrowerWithOverdueLoan() {
        User borrower = user(UserRole.STUDENT, BORROWER_ID);
        stubBorrowerAndCopy(borrower, book());
        when(loanRepository.existsByBook_IdAndStatus(BOOK_ID, LoanStatus.LOST)).thenReturn(false);
        when(loanRepository.existsByBook_IdAndStatus(BOOK_ID, LoanStatus.ACTIVE)).thenReturn(false);
        when(loanRepository.existsByBorrower_UniversityIdAndStatusAndDueAtBefore(
                BORROWER_ID, LoanStatus.ACTIVE, NOW)).thenReturn(true);

        assertThrows(LoanConflictException.class, () -> loanService.borrow(BORROWER_ID, BOOK_ID));

        verify(loanRepository, never()).saveAndFlush(any());
    }

    /** Verifies the active-loan limit counts only current active loans and rejects the fourth. */
    @Test
    void borrow_rejectsBorrowerAtThreeActiveLoans() {
        User borrower = user(UserRole.STUDENT, BORROWER_ID);
        stubBorrowerAndCopy(borrower, book());
        when(loanRepository.existsByBook_IdAndStatus(BOOK_ID, LoanStatus.LOST)).thenReturn(false);
        when(loanRepository.existsByBook_IdAndStatus(BOOK_ID, LoanStatus.ACTIVE)).thenReturn(false);
        when(loanRepository.existsByBorrower_UniversityIdAndStatusAndDueAtBefore(
                BORROWER_ID, LoanStatus.ACTIVE, NOW)).thenReturn(false);
        when(loanRepository.countByBorrower_UniversityIdAndStatus(BORROWER_ID, LoanStatus.ACTIVE))
                .thenReturn(3L);

        assertThrows(LoanConflictException.class, () -> loanService.borrow(BORROWER_ID, BOOK_ID));

        verify(loanRepository, never()).saveAndFlush(any());
    }

    /** Verifies return records the fixed library time and preserves the borrower's history row. */
    @Test
    void returnLoan_recordsReturnForOwnedActiveLoan() {
        Loan loan = activeLoan(user(UserRole.STUDENT, BORROWER_ID), book());
        when(loanRepository.findByIdForUpdate(LOAN_ID)).thenReturn(Optional.of(loan));

        LoanResponseDTO response = loanService.returnLoan(BORROWER_ID, LOAN_ID);

        assertEquals(LoanStatus.RETURNED, response.status());
        assertEquals(NOW, response.returnedAt());
        assertFalse(response.overdue());
        verify(loanRepository).findByIdForUpdate(LOAN_ID);
    }

    /** Verifies another patron receives the same not-found response as an unknown loan ID. */
    @Test
    void returnLoan_hidesAnotherBorrowersLoan() {
        Loan loan = activeLoan(user(UserRole.STUDENT, BORROWER_ID), book());
        when(loanRepository.findByIdForUpdate(LOAN_ID)).thenReturn(Optional.of(loan));

        assertThrows(LoanNotFoundException.class, () -> loanService.returnLoan("2025-9999", LOAN_ID));

        assertEquals(LoanStatus.ACTIVE, loan.getStatus());
    }

    /** Verifies returned and lost loans cannot be transitioned a second time by return. */
    @Test
    void returnLoan_rejectsTerminalLoan() {
        User borrower = user(UserRole.STUDENT, BORROWER_ID);
        User staff = user(UserRole.ADMINISTRATOR, STAFF_ID);
        Loan loan = activeLoan(borrower, book());
        loan.markLost(staff, NOW.minusMinutes(1));
        when(loanRepository.findByIdForUpdate(LOAN_ID)).thenReturn(Optional.of(loan));

        assertThrows(LoanConflictException.class, () -> loanService.returnLoan(BORROWER_ID, LOAN_ID));

        assertEquals(LoanStatus.LOST, loan.getStatus());
    }

    /** Verifies an assistant can mark a student loan lost and the service updates the physical copy. */
    @Test
    void markLost_recordsStaffAndMarksCopyLost() {
        User staff = user(UserRole.ADMINISTRATOR, STAFF_ID);
        User borrower = user(UserRole.STUDENT, BORROWER_ID);
        Book copy = book();
        Loan loan = activeLoan(borrower, copy);
        when(userRepository.findByUniversityId(STAFF_ID)).thenReturn(Optional.of(staff));
        when(loanRepository.findByIdForUpdate(LOAN_ID)).thenReturn(Optional.of(loan));
        when(bookRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(copy));

        LoanResponseDTO response = loanService.markLost(STAFF_ID, LOAN_ID);

        assertEquals(LoanStatus.LOST, response.status());
        assertEquals(NOW, response.lostAt());
        assertEquals(STAFF_ID, response.lostByUniversityId());
        assertEquals(BookCopyStatus.LOST, copy.getCopyStatus());
    }

    /** Verifies patrons cannot use the staff-only lost-resolution service operation. */
    @Test
    void markLost_rejectsStudentCaller() {
        when(userRepository.findByUniversityId(BORROWER_ID))
                .thenReturn(Optional.of(user(UserRole.STUDENT, BORROWER_ID)));

        assertThrows(AccessDeniedException.class, () -> loanService.markLost(BORROWER_ID, LOAN_ID));

        verify(loanRepository, never()).findByIdForUpdate(any());
    }

    /** Verifies assistants cannot resolve loans belonging to other assistants. */
    @Test
    void markLost_rejectsAssistantTarget() {
        User staff = user(UserRole.ADMINISTRATOR, STAFF_ID);
        Loan loan = activeLoan(user(UserRole.ADMINISTRATOR, "2025-2222"), book());
        when(userRepository.findByUniversityId(STAFF_ID)).thenReturn(Optional.of(staff));
        when(loanRepository.findByIdForUpdate(LOAN_ID)).thenReturn(Optional.of(loan));

        assertThrows(AccessDeniedException.class, () -> loanService.markLost(STAFF_ID, LOAN_ID));

        verify(bookRepository, never()).findByIdForUpdate(any());
    }

    /** Verifies librarian history scope includes library assistants. */
    @Test
    void getPatronHistory_allowsLibrarianToViewAssistantHistory() {
        User librarian = user(UserRole.LIBRARIAN, STAFF_ID);
        User assistant = user(UserRole.ADMINISTRATOR, BORROWER_ID);
        when(userRepository.findByUniversityId(STAFF_ID)).thenReturn(Optional.of(librarian));
        when(userRepository.findByUniversityId(BORROWER_ID)).thenReturn(Optional.of(assistant));
        when(loanRepository.findByBorrower_UniversityId(eq(BORROWER_ID), any(Pageable.class)))
                .thenReturn(Page.empty());

        Page<LoanResponseDTO> response = loanService.getPatronHistory(
                STAFF_ID, BORROWER_ID, PageRequest.of(0, 12));

        assertTrue(response.isEmpty());
        verify(loanRepository).findByBorrower_UniversityId(eq(BORROWER_ID), any(Pageable.class));
    }

    /** Verifies assistants cannot inspect another assistant's history. */
    @Test
    void getPatronHistory_rejectsAssistantTargetForAssistantCaller() {
        when(userRepository.findByUniversityId(STAFF_ID))
                .thenReturn(Optional.of(user(UserRole.ADMINISTRATOR, STAFF_ID)));
        when(userRepository.findByUniversityId(BORROWER_ID))
                .thenReturn(Optional.of(user(UserRole.ADMINISTRATOR, BORROWER_ID)));

        assertThrows(AccessDeniedException.class, () -> loanService.getPatronHistory(
                STAFF_ID, BORROWER_ID, PageRequest.of(0, 12)));

        verify(loanRepository, never()).findByBorrower_UniversityId(anyString(), any(Pageable.class));
    }

    /** Verifies paging is clamped and client sorts receive a stable descending ID tie-breaker. */
    @Test
    void getMyHistory_clampsPageAndAddsStableIdSort() {
        when(loanRepository.findByBorrower_UniversityId(eq(BORROWER_ID), any(Pageable.class)))
                .thenReturn(Page.empty());
        Pageable requested = PageRequest.of(2, 150, Sort.by(Sort.Order.asc("status")));

        loanService.getMyHistory(BORROWER_ID, requested);

        ArgumentCaptor<Pageable> safePage = ArgumentCaptor.forClass(Pageable.class);
        verify(loanRepository).findByBorrower_UniversityId(eq(BORROWER_ID), safePage.capture());
        assertEquals(2, safePage.getValue().getPageNumber());
        assertEquals(100, safePage.getValue().getPageSize());
        assertEquals(Sort.Direction.ASC, safePage.getValue().getSort().getOrderFor("status").getDirection());
        assertEquals(Sort.Direction.DESC, safePage.getValue().getSort().getOrderFor("id").getDirection());
    }

    /** Verifies unsupported sort properties are rejected before the repository query runs. */
    @Test
    void getMyHistory_rejectsUnsupportedSortField() {
        Pageable unsafePage = PageRequest.of(0, 12, Sort.by("passwordHash"));

        assertThrows(IllegalArgumentException.class, () -> loanService.getMyHistory(BORROWER_ID, unsafePage));

        verify(loanRepository, never()).findByBorrower_UniversityId(anyString(), any(Pageable.class));
    }

    /** Configures repository results for a borrower and an available physical copy. */
    private void stubAvailableBorrow(User borrower, Book copy) {
        stubBorrowerAndCopy(borrower, copy);
        when(loanRepository.existsByBook_IdAndStatus(BOOK_ID, LoanStatus.LOST)).thenReturn(false);
        when(loanRepository.existsByBook_IdAndStatus(BOOK_ID, LoanStatus.ACTIVE)).thenReturn(false);
        when(loanRepository.existsByBorrower_UniversityIdAndStatusAndDueAtBefore(
                borrower.getUniversityId(), LoanStatus.ACTIVE, NOW)).thenReturn(false);
        when(loanRepository.countByBorrower_UniversityIdAndStatus(
                borrower.getUniversityId(), LoanStatus.ACTIVE)).thenReturn(0L);
    }

    /** Configures the locked patron and physical copy lookups shared by checkout scenarios. */
    private void stubBorrowerAndCopy(User borrower, Book copy) {
        when(userRepository.findByUniversityIdForUpdate(borrower.getUniversityId()))
                .thenReturn(Optional.of(borrower));
        when(bookRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(copy));
    }

    /** Creates a minimal user entity with the supplied persisted role and university ID. */
    private User user(UserRole role, String universityId) {
        User user = new User();
        user.setId(1L);
        user.setUniversityId(universityId);
        user.setUserRole(role);
        return user;
    }

    /** Creates one in-collection physical copy used by service scenarios. */
    private Book book() {
        Book copy = new Book("1984", "George Orwell", "Dystopian", new BigDecimal("19.99"));
        copy.setId(BOOK_ID);
        return copy;
    }

    /** Creates an active loan whose due time falls in the future for the fixed library clock. */
    private Loan activeLoan(User borrower, Book copy) {
        return new Loan(borrower, copy, NOW.minusDays(1), NOW.plusDays(13));
    }
}
