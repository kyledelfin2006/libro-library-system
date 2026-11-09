package app.info.controller;

import app.LibraryApplication;
import app.audit.controller.AuditController;
import app.audit.service.AuditService;
import app.auth.config.JwtConfiguration;
import app.auth.config.SecurityConfig;
import app.global.exception.GlobalExceptionHandler;
import app.info.dto.BuildInfoDTO;
import app.info.service.BuildInfoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Both staff-only read endpoints enforce route roles before service access. */
@ActiveProfiles("test")
@WebMvcTest({BuildInfoController.class, AuditController.class})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtConfiguration.class, GlobalExceptionHandler.class,
        StaffInformationMvcTest.MockBeans.class})
@ContextConfiguration(classes = LibraryApplication.class)
class StaffInformationMvcTest {
    @Autowired MockMvc mvc;
    @Autowired BuildInfoService info;
    @Autowired AuditService audit;

    @TestConfiguration(proxyBeanMethods = false)
    static class MockBeans {
        @Bean BuildInfoService info() { return mock(BuildInfoService.class); }
        @Bean AuditService audit() { return mock(AuditService.class); }
        @Bean app.auth.service.LibroUserDetailsService details() {
            return mock(app.auth.service.LibroUserDetailsService.class);
        }
        @Bean org.springframework.security.crypto.password.PasswordEncoder encoder() {
            return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4);
        }
    }

    @Test
    void infoRequiresStaffAndReturnsOnlySafeFields() throws Exception {
        mvc.perform(get("/app/info")).andExpect(status().isUnauthorized());
        mvc.perform(get("/app/info").with(user("2025-4321").roles("STUDENT")))
                .andExpect(status().isForbidden());
        when(info.current()).thenReturn(new BuildInfoDTO("1.0", "unknown", "abc1234", "local"));
        mvc.perform(get("/app/info").with(user("2025-8765").roles("LIBRARIAN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.commit").value("abc1234"));
    }

    @Test
    void auditRequiresStaff() throws Exception {
        mvc.perform(get("/app/audit")).andExpect(status().isUnauthorized());
        mvc.perform(get("/app/audit").with(user("2025-4321").roles("FACULTY")))
                .andExpect(status().isForbidden());
        when(audit.list(eq("2025-8765"), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());
        mvc.perform(get("/app/audit").with(user("2025-8765").roles("ADMINISTRATOR")))
                .andExpect(status().isOk());
    }
}
