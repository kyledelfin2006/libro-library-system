package app.loan.controller;

import app.global.config.ErrorApiResponse;
import app.global.responses.ApiResponse;
import app.loan.dto.LoanResponseDTO;
import app.loan.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for borrowing, returning, loss resolution, and loan history.
 */
@RestController
@RequestMapping("/app/loans")
@Tag(name = "Loans", description = "Borrowing, returns, lost-copy resolution, and loan history")
public class LoanAPI {

    private final LoanService loanService;

    /**
     * Creates the loan controller.
     *
     * @param loanService loan business operations
     */
    public LoanAPI(LoanService loanService) {
        this.loanService = loanService;
    }

    /**
     * Starts a loan for the authenticated student or faculty member.
     *
     * @param bookId physical copy identifier from the route
     * @param authentication authenticated caller supplied by Spring Security
     * @return HTTP 201 with the new loan response
     */
    @PostMapping("/{bookId}")
    @Operation(summary = "Borrow a book copy",
            description = "Students and faculty may borrow an in-collection physical copy. Borrowing is blocked when the caller has an overdue loan or already has three active loans. Due time is 14 calendar days after checkout in Asia/Manila local time.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Copy borrowed")
    @ErrorApiResponse(responseCode = "403", description = "Only students and faculty may borrow")
    @ErrorApiResponse(responseCode = "404", description = "Book copy not found")
    @ErrorApiResponse(responseCode = "409", description = "Copy is already borrowed or lost, or borrower cannot borrow yet")
    public ResponseEntity<ApiResponse<LoanResponseDTO>> borrow(
            @Parameter(description = "Identifier of one physical book copy.", example = "42")
            @PathVariable Long bookId,
            Authentication authentication
    ) {
        // Borrower identity comes from the authenticated principal, never from a request body.
        LoanResponseDTO loan = loanService.borrow(authentication.getName(), bookId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Book borrowed successfully", loan));
    }

    /**
     * Records the return of the caller's active loan.
     *
     * @param loanId loan identifier from the route
     * @param authentication authenticated caller supplied by Spring Security
     * @return HTTP 200 with the returned loan response
     */
    @PutMapping("/{loanId}/return")
    @Operation(summary = "Return a borrowed book",
            description = "A student or faculty member can return only their own active loan. The returned event remains in history.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Copy returned")
    @ErrorApiResponse(responseCode = "403", description = "Only students and faculty may return loans")
    @ErrorApiResponse(responseCode = "404", description = "Loan not found or not owned by caller")
    @ErrorApiResponse(responseCode = "409", description = "Loan is already returned or marked lost")
    public ResponseEntity<ApiResponse<LoanResponseDTO>> returnLoan(
            @PathVariable Long loanId,
            Authentication authentication
    ) {
        // Pass the authenticated identity so the service can enforce loan ownership.
        LoanResponseDTO loan = loanService.returnLoan(authentication.getName(), loanId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Book returned", loan));
    }

    /**
     * Marks an active patron loan and its physical copy as lost.
     *
     * @param loanId loan identifier from the route
     * @param authentication authenticated librarian or library assistant
     * @return HTTP 200 with the loss resolution and staff audit data
     */
    @PutMapping("/{loanId}/lost")
    @Operation(summary = "Mark a loan as lost",
            description = "Library assistants may resolve student/faculty loans as lost. Librarians may also resolve library-assistant loans. The physical copy becomes unavailable, and the loan remains in history with the staff member and time recorded.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Loan marked lost")
    @ErrorApiResponse(responseCode = "403", description = "Caller cannot resolve this patron's loan")
    @ErrorApiResponse(responseCode = "404", description = "Loan not found")
    @ErrorApiResponse(responseCode = "409", description = "Loan is no longer active")
    public ResponseEntity<ApiResponse<LoanResponseDTO>> markLost(
            @PathVariable Long loanId,
            Authentication authentication
    ) {
        // The authenticated staff account is recorded as the resolver by the service.
        LoanResponseDTO loan = loanService.markLost(authentication.getName(), loanId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Loan marked lost", loan));
    }

    /**
     * Lists the authenticated caller's own loan history.
     *
     * @param pageable requested page and size
     * @param authentication authenticated caller supplied by Spring Security
     * @return the caller's page of active and historical loans
     */
    @GetMapping("/me")
    @Operation(summary = "View my loan history",
            description = "Returns active, returned, and lost loans for the authenticated caller. Page size defaults to 12 and is capped at 100. Overdue is calculated at request time.")
    @ErrorApiResponse(responseCode = "400", description = "Unsupported sort property")
    public ResponseEntity<Page<LoanResponseDTO>> myHistory(
            @ParameterObject @PageableDefault(size = 12, sort = {"borrowedAt", "id"}, direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable,
            Authentication authentication
    ) {
        // Use the caller principal so clients cannot select another account's own-history query.
        return ResponseEntity.ok(loanService.getMyHistory(authentication.getName(), pageable));
    }

    /**
     * Lists one patron's loan history for an authorized library staff member.
     *
     * @param universityId target patron university ID
     * @param pageable requested page and size
     * @param authentication authenticated staff caller supplied by Spring Security
     * @return the target patron's authorized loan-history page
     */
    @GetMapping("/users/{universityId}")
    @Operation(summary = "View a patron's loan history",
            description = "Library assistants may view student/faculty histories. Librarians may also view library-assistant histories. Neither staff role may view another librarian's history. Page size defaults to 12 and is capped at 100.")
    @ErrorApiResponse(responseCode = "403", description = "Caller cannot view this patron's history")
    @ErrorApiResponse(responseCode = "404", description = "Patron account not found")
    @ErrorApiResponse(responseCode = "400", description = "Unsupported sort property")
    public ResponseEntity<Page<LoanResponseDTO>> patronHistory(
            @Parameter(description = "University ID of the patron whose history is requested.", example = "2025-4321")
            @PathVariable String universityId,
            @ParameterObject @PageableDefault(size = 12, sort = {"borrowedAt", "id"}, direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable,
            Authentication authentication
    ) {
        // The service checks both the staff role and the target patron role.
        return ResponseEntity.ok(loanService.getPatronHistory(
                authentication.getName(), universityId, pageable));
    }
}
