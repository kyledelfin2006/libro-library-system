package unit.loan;

import app.LibraryApplication;
import app.auth.SecurityConfig;
import app.global.exceptions.GlobalExceptionHandler;
import app.loan.controller.LoanAPI;
import app.loan.dto.LoanResponseDTO;
import app.loan.entity.LoanStatus;
import app.loan.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MVC tests for loan route authentication, role gates, CSRF, and principal-derived ownership. */
@WebMvcTest(LoanAPI.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, GlobalExceptionHandler.class, LoanSecurityMvcTest.MockBeans.class})
@ContextConfiguration(classes = LibraryApplication.class)
class LoanSecurityMvcTest {

    private static final String BORROWER_ID = "2025-4321";
    private static final Long BOOK_ID = 42L;
    private static final Long LOAN_ID = 81L;
    private static final LocalDateTime BORROWED_AT = LocalDateTime.of(2026, 10, 19, 10, 30);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoanService loanService;

    @TestConfiguration(proxyBeanMethods = false)
    static class MockBeans {

        /** Supplies the controller's service dependency without loading persistence. */
        @Bean
        LoanService loanService() {
            return mock(LoanService.class);
        }
    }

    /** Resets the service mock so every route test verifies only its own interactions. */
    @BeforeEach
    void resetLoanService() {
        reset(loanService);
    }

    /** Verifies students borrow using their authenticated principal rather than request data. */
    @Test
    void studentCanBorrowAndServiceReceivesAuthenticatedIdentity() throws Exception {
        when(loanService.borrow(BORROWER_ID, BOOK_ID)).thenReturn(loanResponse(LoanStatus.ACTIVE));

        mockMvc.perform(post("/app/loans/{bookId}", BOOK_ID)
                        .with(user(BORROWER_ID).roles("STUDENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.borrowerUniversityId").value(BORROWER_ID))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(loanService).borrow(BORROWER_ID, BOOK_ID);
    }

    /** Verifies library assistants are denied checkout before the service is called. */
    @Test
    void assistantCannotBorrow() throws Exception {
        mockMvc.perform(post("/app/loans/{bookId}", BOOK_ID)
                        .with(user("2025-8765").roles("ADMINISTRATOR"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(loanService);
    }

    /** Verifies faculty may return their own loan through the student/faculty route gate. */
    @Test
    void facultyCanReturnOwnLoan() throws Exception {
        when(loanService.returnLoan(BORROWER_ID, LOAN_ID)).thenReturn(loanResponse(LoanStatus.RETURNED));

        mockMvc.perform(put("/app/loans/{loanId}/return", LOAN_ID)
                        .with(user(BORROWER_ID).roles("FACULTY"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"));

        verify(loanService).returnLoan(BORROWER_ID, LOAN_ID);
    }

    /** Verifies a student cannot invoke the staff-only lost-copy route. */
    @Test
    void studentCannotMarkLoanLost() throws Exception {
        mockMvc.perform(put("/app/loans/{loanId}/lost", LOAN_ID)
                        .with(user(BORROWER_ID).roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(loanService);
    }

    /** Verifies assistants may submit an authorized lost-loan resolution. */
    @Test
    void assistantCanMarkLoanLost() throws Exception {
        when(loanService.markLost("2025-8765", LOAN_ID)).thenReturn(loanResponse(LoanStatus.LOST));

        mockMvc.perform(put("/app/loans/{loanId}/lost", LOAN_ID)
                        .with(user("2025-8765").roles("ADMINISTRATOR"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOST"));

        verify(loanService).markLost("2025-8765", LOAN_ID);
    }

    /** Verifies staff can request their own history and the caller ID comes from authentication. */
    @Test
    void authenticatedPatronCanReadOwnHistory() throws Exception {
        when(loanService.getMyHistory(org.mockito.ArgumentMatchers.eq(BORROWER_ID),
                org.mockito.ArgumentMatchers.any())).thenReturn(Page.empty());

        mockMvc.perform(get("/app/loans/me").with(user(BORROWER_ID).roles("STUDENT")))
                .andExpect(status().isOk());

        verify(loanService).getMyHistory(org.mockito.ArgumentMatchers.eq(BORROWER_ID),
                org.mockito.ArgumentMatchers.any());
    }

    /** Verifies anonymous users cannot access loan history. */
    @Test
    void anonymousCallerCannotReadLoanHistory() throws Exception {
        mockMvc.perform(get("/app/loans/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(loanService);
    }

    /** Verifies CSRF protection blocks an otherwise authorized unsafe request without a token. */
    @Test
    void borrowRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/app/loans/{bookId}", BOOK_ID)
                        .with(user(BORROWER_ID).roles("STUDENT")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(loanService);
    }

    /** Creates a response value sufficient for asserting controller status and serialization. */
    private LoanResponseDTO loanResponse(LoanStatus status) {
        return new LoanResponseDTO(
                LOAN_ID,
                BORROWER_ID,
                BOOK_ID,
                "1984",
                BORROWED_AT,
                BORROWED_AT.plusDays(14),
                status == LoanStatus.RETURNED ? BORROWED_AT.plusDays(1) : null,
                status,
                false,
                status == LoanStatus.LOST ? BORROWED_AT.plusDays(1) : null,
                status == LoanStatus.LOST ? "2025-8765" : null);
    }
}
