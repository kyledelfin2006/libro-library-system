package app.user.service;

import app.user.entity.enums.UserRole;
import app.user.mapper.UserMapper;
import app.user.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Directory filters remain inside the caller's target-role scope. */
class DirectorySearchTest {
    @Test
    void assistantCannotFilterForAdministratorsAndSearchKeepsScopedRoles() {
        UserRepository repository = mock(UserRepository.class);
        UserService service = new UserService(repository, mock(PasswordEncoder.class),
                mock(UserMapper.class), mock(Validator.class), new UserAccessPolicy(), mock(MeterRegistry.class));
        var roles = List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));
        var page = PageRequest.of(0, 12);

        assertThrows(AccessDeniedException.class, () -> service.getAllUsers(
                page, "2025-4321", roles, UserRole.ADMINISTRATOR, "name"));
        verifyNoInteractions(repository);

        when(repository.searchDirectory(anyCollection(), isNull(), eq("name"), eq(page)))
                .thenReturn(new PageImpl<>(List.of()));
        service.getAllUsers(page, "2025-4321", roles, null, "name");
        verify(repository).searchDirectory(eq(List.of(UserRole.STUDENT, UserRole.FACULTY)),
                isNull(), eq("name"), eq(page));
    }
}
