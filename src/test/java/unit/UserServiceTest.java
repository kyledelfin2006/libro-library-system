package unit;

import app.user.dto.UserCreateUpdateDTO;
import app.user.dto.UserResponseDTO;
import app.user.dto.ChangePasswordDTO;
import app.user.entity.User;
import app.user.mapper.UserMapper;
import app.user.repository.UserRepository;
import app.user.service.UserService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the user update service contract.
 *
 * <p>The tests verify normalization, partial-update behavior, validation,
 * email uniqueness, and the intentional absence of an explicit save call.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserServiceTest {

    /** Repository mock used to control lookup and duplicate-email behavior. */
    private final UserRepository repository = mock(UserRepository.class);

    /** Password encoder dependency required by the service constructor. */
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    /** Shared validator factory for DTO constraint tests. */
    private final ValidatorFactory validatorFactory =
            Validation.buildDefaultValidatorFactory();

    /** Service under test using a real mapper and validator. */
    private final UserService userService = new UserService(
            repository,
            passwordEncoder,
            new UserMapper(),
            validatorFactory.getValidator()
    );

    /** Persisted user fixture rebuilt before every scenario. */
    private User existingUser;

    /** Closes the shared validator factory after all tests finish. */
    @AfterAll
    void closeValidatorFactory() {
        validatorFactory.close();
    }

    /** Resets repository behavior and creates the existing-user fixture. */
    @BeforeEach
    void setUp() {
        reset(repository);

        existingUser = new User();
        existingUser.setId(1L);
        existingUser.setUniversityId("2025-4321");
        existingUser.setFirstName("Old First");
        existingUser.setLastName("Old Last");
        existingUser.setMiddleInitial("A");
        existingUser.setEmail("old@example.com");

        when(repository.findByUniversityId("2025-4321"))
                .thenReturn(Optional.of(existingUser));
    }

    /** Verifies supplied values are trimmed, normalized, and applied. */
    @Test
    void updateUser_shouldNormalizeAndUpdateSuppliedFields() {
        UserCreateUpdateDTO request = new UserCreateUpdateDTO(
                " New First ",
                " New Last ",
                " B ",
                " NEW@Example.COM "
        );

        UserResponseDTO result = userService.updateUser(" 2025-4321 ", request);

        assertEquals("2025-4321", result.getUniversityId());
        assertEquals("New First", result.getFirstName());
        assertEquals("New Last", result.getLastName());
        assertEquals("B", result.getMiddleInitial());
        assertEquals("new@example.com", result.getEmail());
        verify(repository).existsByEmailIgnoreCase("new@example.com");
        verify(repository, never()).save(any(User.class));
    }

    /** Verifies omitted fields remain unchanged during a partial update. */
    @Test
    void updateUser_shouldPreserveOmittedFields() {
        UserResponseDTO result = userService.updateUser(
                "2025-4321",
                new UserCreateUpdateDTO("New First", null, null, null)
        );

        assertEquals("New First", result.getFirstName());
        assertEquals("Old Last", result.getLastName());
        assertEquals("A", result.getMiddleInitial());
        assertEquals("old@example.com", result.getEmail());
        verify(repository, never()).existsByEmailIgnoreCase(anyString());
    }

    /** Verifies a missing update body is rejected before repository access. */
    @Test
    void updateUser_whenRequestIsNull_shouldReject() {
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.updateUser("2025-4321", null)
        );

        verifyNoInteractions(repository);
    }

    /** Verifies DTO constraints are evaluated for supplied update values. */
    @Test
    void updateUser_whenValueViolatesDtoConstraint_shouldReject() {
        String tooLongName = "x".repeat(51);

        assertThrows(
                ConstraintViolationException.class,
                () -> userService.updateUser(
                        "2025-4321",
                        new UserCreateUpdateDTO(tooLongName, null, null, null)
                )
        );

        verify(repository, never()).save(any(User.class));
    }

    /** Verifies blank names are rejected even though @Size permits blank text. */
    @Test
    void updateUser_whenNameIsBlank_shouldReject() {
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.updateUser(
                        "2025-4321",
                        new UserCreateUpdateDTO("   ", null, null, null)
                )
        );
    }

    /** Verifies retaining the current email does not trigger a duplicate check. */
    @Test
    void updateUser_whenEmailIsUnchanged_shouldAllowIt() {
        UserResponseDTO result = userService.updateUser(
                "2025-4321",
                new UserCreateUpdateDTO(null, null, null, " OLD@EXAMPLE.COM ")
        );

        assertEquals("old@example.com", result.getEmail());
        verify(repository, never()).existsByEmailIgnoreCase(anyString());
    }

    /** Verifies changing to another user's email is rejected. */
    @Test
    void updateUser_whenEmailBelongsToAnotherUser_shouldReject() {
        when(repository.existsByEmailIgnoreCase("other@example.com"))
                .thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.updateUser(
                        "2025-4321",
                        new UserCreateUpdateDTO(null, null, null, "other@example.com")
                )
        );

        assertEquals("old@example.com", existingUser.getEmail());
    }

    /** Verifies a valid current password produces and stores a new encoded hash. */
    @Test
    void updatePassword_shouldVerifyAndEncodeNewPassword() {
        existingUser.setPasswordHash("old-hash");
        when(passwordEncoder.matches("1234", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("5678")).thenReturn("new-hash");

        userService.updatePassword(
                "2025-4321",
                new ChangePasswordDTO("1234", "5678")
        );

        assertEquals("new-hash", existingUser.getPasswordHash());
        verify(passwordEncoder).matches("1234", "old-hash");
        verify(passwordEncoder).encode("5678");
        verify(repository, never()).save(any(User.class));
    }

    /** Verifies an incorrect current password prevents the hash from changing. */
    @Test
    void updatePassword_whenCurrentPasswordIsIncorrect_shouldReject() {
        existingUser.setPasswordHash("old-hash");
        when(passwordEncoder.matches("0000", "old-hash")).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.updatePassword(
                        "2025-4321",
                        new ChangePasswordDTO("0000", "5678")
                )
        );

        assertEquals("old-hash", existingUser.getPasswordHash());
        verify(passwordEncoder, never()).encode(anyString());
    }

    /** Verifies invalid password input is rejected before user lookup. */
    @Test
    void updatePassword_whenPasswordFormatIsInvalid_shouldReject() {
        assertThrows(
                ConstraintViolationException.class,
                () -> userService.updatePassword(
                        "2025-4321",
                        new ChangePasswordDTO("123", "5678")
                )
        );

        verify(repository, never()).findByUniversityId(anyString());
    }

    /** Verifies a null password request is rejected before repository access. */
    @Test
    void updatePassword_whenRequestIsNull_shouldReject() {
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.updatePassword("2025-4321", null)
        );

        verifyNoInteractions(repository);
    }
}
