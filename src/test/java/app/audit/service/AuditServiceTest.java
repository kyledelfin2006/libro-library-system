package app.audit.service;

import app.audit.entity.AuditAction;
import app.audit.entity.AuditEntityType;
import app.audit.entity.AuditEvent;
import app.audit.repository.AuditRepository;
import app.user.entity.User;
import app.user.entity.enums.UserRole;
import app.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Audit writes have controlled fields and reads reject out-of-scope role filters. */
class AuditServiceTest {
    @Test
    void recordContainsOnlyFixedActionAndAuthenticatedActor() {
        AuditRepository repository = mock(AuditRepository.class);
        AuditService service = new AuditService(repository, mock(UserRepository.class));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "2025-4321", "unused", List.of(new SimpleGrantedAuthority("ROLE_LIBRARIAN"))));
        try {
            service.record(AuditAction.PASSWORD_RESET, AuditEntityType.USER, "2025-8765", UserRole.STUDENT);
            var saved = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
            verify(repository).save(saved.capture());
            assertEquals("2025-4321", saved.getValue().getActorUniversityId());
            assertEquals("PASSWORD_RESET", saved.getValue().getAction());
            assertEquals("password reset", saved.getValue().getSummary());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void recordedActorRoleIgnoresNonRoleAuthorities() {
        AuditRepository repository = mock(AuditRepository.class);
        AuditService service = new AuditService(repository, mock(UserRepository.class));
        // A bearer-token login can carry FACTOR_BEARER ahead of the role; the role must still be recorded.
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "2020-0002", "unused", List.of(new SimpleGrantedAuthority("FACTOR_BEARER"),
                new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"))));
        try {
            service.record(AuditAction.BOOK_CREATED, AuditEntityType.BOOK, "13", null);
            var saved = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
            verify(repository).save(saved.capture());
            assertEquals("ADMINISTRATOR", saved.getValue().getActorRole());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** Public signup is anonymous, so the new student account is recorded as its own actor. */
    @Test
    void anonymousStudentSignupIsAttributedToTheNewAccount() {
        AuditRepository repository = mock(AuditRepository.class);
        AuditService service = new AuditService(repository, mock(UserRepository.class));
        SecurityContextHolder.clearContext();

        service.record(AuditAction.ACCOUNT_CREATED, AuditEntityType.USER, "2025-0001", UserRole.STUDENT);

        var saved = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(saved.capture());
        assertEquals("2025-0001", saved.getValue().getActorUniversityId());
        assertEquals("SYSTEM", saved.getValue().getActorRole());
        assertEquals("USER", saved.getValue().getEntityType());
    }

    /** Any other anonymous change, including creating a privileged account, is attributed to the system. */
    @Test
    void anonymousNonSignupChangeIsAttributedToTheSystem() {
        AuditRepository repository = mock(AuditRepository.class);
        AuditService service = new AuditService(repository, mock(UserRepository.class));
        SecurityContextHolder.clearContext();

        service.record(AuditAction.ACCOUNT_CREATED, AuditEntityType.USER, "2025-0002", UserRole.ADMINISTRATOR);

        var saved = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(saved.capture());
        assertEquals("system", saved.getValue().getActorUniversityId());
    }

    @Test
    void assistantCannotRequestPrivilegedTargetRole() {
        AuditRepository repository = mock(AuditRepository.class);
        UserRepository users = mock(UserRepository.class);
        User assistant = new User();
        assistant.setUserRole(UserRole.ADMINISTRATOR);
        when(users.findByUniversityId("2025-4321")).thenReturn(Optional.of(assistant));
        AuditService service = new AuditService(repository, users);
        assertThrows(AccessDeniedException.class, () -> service.list("2025-4321", null,
                null, null, UserRole.LIBRARIAN, null, null, PageRequest.of(0, 12)));
        verifyNoInteractions(repository);
    }
}
