package app.loan.service;

import app.book.entity.Book;
import app.book.entity.BookCopyStatus;
import app.book.exceptions.BookNotFoundException;
import app.book.repository.BookRepository;
import app.loan.dto.LoanResponseDTO;
import app.loan.entity.Loan;
import app.loan.entity.LoanStatus;
import app.loan.exception.LoanConflictException;
import app.loan.exception.LoanNotFoundException;
import app.loan.mapper.LoanMapper;
import app.loan.repository.LoanRepository;
import app.user.entity.User;
import app.user.entity.enums.UserRole;
import app.user.exception.UserNotFoundException;
import app.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Coordinates checkout, return, lost-copy resolution, and role-scoped history.
 */
@Service
@RequiredArgsConstructor
public class LoanService {

    private static final int MAX_ACTIVE_LOANS = 3;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "borrowedAt", "dueAt", "returnedAt", "status");

    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final LoanMapper loanMapper;
    private final Clock libraryClock;

    /**
     * Borrows an available physical copy for the authenticated student or faculty member.
     *
     * @param callerUniversityId university ID from the authenticated principal
     * @param bookId physical copy identifier
     * @return the newly created loan response
     * @throws UserNotFoundException if the authenticated account no longer exists
     * @throws BookNotFoundException if the physical copy does not exist
     * @throws AccessDeniedException if the caller is not a student or faculty member
     * @throws LoanConflictException if the caller is overdue, at the loan limit, or the copy is unavailable
     */
    @Transactional
    public LoanResponseDTO borrow(String callerUniversityId, Long bookId) {
        // Lock the borrower so simultaneous checkouts cannot both pass the three-loan limit.
        User borrower = userRepository.findByUniversityIdForUpdate(callerUniversityId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Only student patrons and faculty patrons may create new loans.
        if (borrower.getUserRole() != UserRole.STUDENT && borrower.getUserRole() != UserRole.FACULTY) {
            throw new AccessDeniedException("Only students and faculty may borrow books");
        }

        // Lock the copy so two different borrowers cannot pass the availability check together.
        Book book = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new BookNotFoundException("Book not found: " + bookId));

        // A lost physical item remains in history and must never be loaned again.
        if (book.getCopyStatus() == BookCopyStatus.LOST
                || loanRepository.existsByBook_IdAndStatus(bookId, LoanStatus.LOST)) {
            throw new LoanConflictException("Book copy has been marked lost");
        }

        // The partial unique index remains the final guard for one active loan per copy.
        if (loanRepository.existsByBook_IdAndStatus(bookId, LoanStatus.ACTIVE)) {
            throw new LoanConflictException("Book copy is already borrowed");
        }

        // Use one clock reading for the overdue check and the loan's checkout timestamp.
        LocalDateTime borrowedAt = LocalDateTime.now(libraryClock);
        if (loanRepository.existsByBorrower_UniversityIdAndStatusAndDueAtBefore(
                callerUniversityId, LoanStatus.ACTIVE, borrowedAt)) {
            throw new LoanConflictException("Return overdue loans before borrowing again");
        }

        // Returned and lost loans remain historical but do not count toward the active limit.
        long activeLoanCount = loanRepository.countByBorrower_UniversityIdAndStatus(
                callerUniversityId, LoanStatus.ACTIVE);
        if (activeLoanCount >= MAX_ACTIVE_LOANS) {
            throw new LoanConflictException("A borrower may have at most three active loans");
        }

        // The agreed loan period is 14 calendar days with no weekend or holiday extension.
        Loan loan = new Loan(borrower, book, borrowedAt, borrowedAt.plusDays(14));
        Loan savedLoan = loanRepository.saveAndFlush(loan);

        // Map lazy relations before the transaction closes.
        return loanMapper.toResponse(savedLoan, borrowedAt);
    }

    /**
     * Returns an active loan owned by the authenticated borrower.
     *
     * @param callerUniversityId university ID from the authenticated principal
     * @param loanId loan identifier
     * @return the updated loan response
     * @throws LoanNotFoundException if the loan is missing or belongs to another borrower
     * @throws LoanConflictException if the loan has already been returned or marked lost
     */
    @Transactional
    public LoanResponseDTO returnLoan(String callerUniversityId, Long loanId) {
        // Lock the loan so concurrent return or lost actions cannot both change its state.
        Loan loan = loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new LoanNotFoundException("Loan not found"));

        // Use the same not-found response for unknown and another person's loan IDs.
        if (!loan.getBorrower().getUniversityId().equals(callerUniversityId)) {
            throw new LoanNotFoundException("Loan not found");
        }

        // A returned or lost loan is terminal and cannot be returned a second time.
        if (!loan.isActive()) {
            throw new LoanConflictException("Loan is no longer active");
        }

        // Record the actual return time; the entity moves from ACTIVE to RETURNED.
        LocalDateTime returnedAt = LocalDateTime.now(libraryClock);
        loan.markReturned(returnedAt);

        // Dirty checking persists the state transition when the transaction commits.
        return loanMapper.toResponse(loan, returnedAt);
    }

    /**
     * Marks an active loan and its physical copy as lost for authorized library staff.
     *
     * @param callerUniversityId university ID from the authenticated staff principal
     * @param loanId loan identifier to resolve
     * @return the lost loan response with staff audit data
     * @throws UserNotFoundException if the authenticated staff account no longer exists
     * @throws LoanNotFoundException if the loan does not exist
     * @throws AccessDeniedException if the caller or target patron is outside the staff scope
     * @throws LoanConflictException if the loan is no longer active
     */
    @Transactional
    public LoanResponseDTO markLost(String callerUniversityId, Long loanId) {
        // Read the staff role from the persisted account, not from a client-supplied field.
        User staff = userRepository.findByUniversityId(callerUniversityId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Assistants and librarians can resolve lost loans; patrons cannot.
        if (staff.getUserRole() != UserRole.ADMINISTRATOR && staff.getUserRole() != UserRole.LIBRARIAN) {
            throw new AccessDeniedException("Only library assistants and librarians may mark loans lost");
        }

        // Lock the event so return and loss cannot both win the terminal transition.
        Loan loan = loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new LoanNotFoundException("Loan not found"));

        // Apply the same target-role visibility boundary used for staff loan histories.
        if (!canManageLoanBorrower(staff.getUserRole(), loan.getBorrower().getUserRole())) {
            throw new AccessDeniedException("You are not allowed to resolve this patron's loan");
        }

        // Only active loans can be resolved as lost.
        if (!loan.isActive()) {
            throw new LoanConflictException("Only active loans can be marked lost");
        }

        // Lock and update the copy in the same transaction so lost inventory cannot be re-loaned.
        Book book = bookRepository.findByIdForUpdate(loan.getBook().getId())
                .orElseThrow(() -> new BookNotFoundException("Book not found: " + loan.getBook().getId()));
        LocalDateTime lostAt = LocalDateTime.now(libraryClock);
        loan.markLost(staff, lostAt);
        book.markLost();

        // The lost loan remains in history and is no longer overdue or active.
        return loanMapper.toResponse(loan, lostAt);
    }

    /**
     * Returns the authenticated caller's loan history.
     *
     * @param callerUniversityId university ID from the authenticated principal
     * @param pageable requested page, size, and sort order
     * @return the caller's history, including active and terminal loans
     */
    @Transactional(readOnly = true)
    public Page<LoanResponseDTO> getMyHistory(String callerUniversityId, Pageable pageable) {
        // Clamp the page size and restrict client-controlled sort properties.
        Pageable safePageable = normalizePageable(pageable);
        LocalDateTime now = LocalDateTime.now(libraryClock);

        // The repository fetches relations needed by the response in the same query.
        return loanRepository.findByBorrower_UniversityId(callerUniversityId, safePageable)
                .map(loan -> loanMapper.toResponse(loan, now));
    }

    /**
     * Returns a patron's loan history when permitted by the staff role matrix.
     *
     * @param callerUniversityId university ID from the authenticated staff principal
     * @param targetUniversityId university ID of the patron whose history is requested
     * @param pageable requested page, size, and sort order
     * @return the authorized patron's history
     * @throws UserNotFoundException if caller or target account does not exist
     * @throws AccessDeniedException if the caller cannot inspect the target role
     */
    @Transactional(readOnly = true)
    public Page<LoanResponseDTO> getPatronHistory(
            String callerUniversityId,
            String targetUniversityId,
            Pageable pageable
    ) {
        // Load both roles so staff access is checked against the requested patron, not just the route.
        User staff = userRepository.findByUniversityId(callerUniversityId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        User patron = userRepository.findByUniversityId(targetUniversityId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Enforce assistant versus librarian history scope in the service layer.
        if (!canManageLoanBorrower(staff.getUserRole(), patron.getUserRole())) {
            throw new AccessDeniedException("You are not allowed to view this patron's loan history");
        }

        // Apply the same page bounds and ordering policy as personal history.
        Pageable safePageable = normalizePageable(pageable);
        LocalDateTime now = LocalDateTime.now(libraryClock);

        // Return only loans owned by the authorized target account.
        return loanRepository.findByBorrower_UniversityId(targetUniversityId, safePageable)
                .map(loan -> loanMapper.toResponse(loan, now));
    }

    /**
     * Applies pagination bounds, an allowed sort-field list, and a stable ID tie-breaker.
     *
     * @param pageable Spring-bound page request
     * @return safe page request for repository use
     */
    private Pageable normalizePageable(Pageable pageable) {
        // Reject fields that do not map to safe and supported Loan properties.
        if (pageable.getSort().stream().anyMatch(order -> !SORTABLE_FIELDS.contains(order.getProperty()))) {
            throw new IllegalArgumentException("Unsupported loan sort field");
        }

        // Use the documented order when the client does not request sorting.
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.DESC, "borrowedAt");

        // Add the primary key so equal timestamps never make page ordering unstable.
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        }

        // Enforce the API's documented maximum even if resolver configuration changes.
        int pageSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        return PageRequest.of(pageable.getPageNumber(), pageSize, sort);
    }

    /**
     * Checks whether a staff role may inspect or resolve the target patron's loans.
     *
     * @param staffRole authenticated staff role
     * @param borrowerRole role of the loan's borrower
     * @return true when the staff role has access to that borrower's history
     */
    private boolean canManageLoanBorrower(UserRole staffRole, UserRole borrowerRole) {
        // Library assistants can access student/faculty loans; librarians can also access assistant loans.
        return switch (staffRole) {
            case ADMINISTRATOR -> borrowerRole == UserRole.STUDENT || borrowerRole == UserRole.FACULTY;
            case LIBRARIAN -> borrowerRole == UserRole.STUDENT
                    || borrowerRole == UserRole.FACULTY
                    || borrowerRole == UserRole.ADMINISTRATOR;
            default -> false;
        };
    }
}
