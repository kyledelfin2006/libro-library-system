package app.user.service;
import app.global.exception.InvalidRequestException;

import app.user.dto.UserPatchRequestDTO;
import app.user.dto.UserResponseDTO;
import app.user.dto.PasswordChangeRequestDTO;
import app.user.dto.PasswordResetRequestDTO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import app.user.dto.UserCreateRequestDTO;
import app.user.entity.User;
import app.user.entity.enums.UserCourse;
import app.user.entity.enums.UserITMajor;
import app.user.entity.enums.UserRole;
import app.user.exception.UserNotFoundException;
import app.user.mapper.UserMapper;
import app.user.repository.UserRepository;
import app.user.validation.PasswordPolicy;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

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

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    /** Service under test using a real mapper and validator. */
    private final UserService userService = new UserService(
            repository,
            passwordEncoder,
            new UserMapper(),
            validatorFactory.getValidator(),
            new UserAccessPolicy(),
            meterRegistry
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
        reset(passwordEncoder);

        existingUser = new User();
        existingUser.setId(1L);
        existingUser.setUniversityId("2025-4321");
        existingUser.setFirstName("Old First");
        existingUser.setLastName("Old Last");
        existingUser.setMiddleInitial("A");
        existingUser.setEmail("old@example.com");
        existingUser.setUserRole(UserRole.STUDENT);

        when(repository.findByUniversityId("2025-4321"))
                .thenReturn(Optional.of(existingUser));
    }

    /** Confirms the BCrypt input guard accepts exactly 72 UTF-8 bytes and rejects overflow. */
    @Test
    void bcryptPasswordByteBoundaryAccepts72AndRejectsMore() {
        assertTrue(PasswordPolicy.isWithinBcryptByteLimit("Ä".repeat(36)));
        assertFalse(PasswordPolicy.isWithinBcryptByteLimit("Ä".repeat(37)));
        assertFalse(PasswordPolicy.isWithinBcryptByteLimit("A".repeat(73)));
    }

    /** Verifies supplied values are trimmed, normalized, and applied. */
    @Test
    void updateUser_shouldNormalizeAndUpdateSuppliedFields() {
        UserPatchRequestDTO request = new UserPatchRequestDTO(
                " New First ",
                " New Last ",
                " B ",
                " NEW@Example.COM "
        );

        UserResponseDTO result = updateUser(" 2025-4321 ", request);

        assertEquals("2025-4321", result.getUniversityId());
        assertEquals("New First", result.getFirstName());
        assertEquals("New Last", result.getLastName());
        assertEquals("B", result.getMiddleInitial());
        assertEquals("new@example.com", result.getEmail());
        verify(repository).existsByEmailIgnoreCase("new@example.com");
        verify(repository, never()).save(any(User.class));
    }

    /** Verifies the public user fields remain sortable through pagination. */
    @Test
    void getAllUsers_withAllowedSortField_shouldReturnPage() {
        Pageable pageable = PageRequest.of(0, 12, Sort.by("firstName"));
        when(repository.findByUserRoleIn(List.of(UserRole.STUDENT, UserRole.FACULTY), pageable))
                .thenReturn(Page.empty());

        Page<UserResponseDTO> result = getAllUsers(pageable);

        assertTrue(result.isEmpty());
        verify(repository).findByUserRoleIn(List.of(UserRole.STUDENT, UserRole.FACULTY), pageable);
    }

    /** Verifies unsupported persistence properties are rejected before querying. */
    @Test
    void getAllUsers_withUnsupportedSortField_shouldReject() {
        Pageable pageable = PageRequest.of(0, 12, Sort.by("password"));

        assertThrows(IllegalArgumentException.class, () -> getAllUsers(pageable));

        verifyNoInteractions(repository);
    }

    @Test
    void getAllUsers_librarianCanSeeLibraryAssistantAccounts() {
        Pageable pageable = PageRequest.of(0, 12);
        List<UserRole> visibleRoles = List.of(
                UserRole.STUDENT, UserRole.FACULTY, UserRole.ADMINISTRATOR);
        when(repository.findByUserRoleIn(visibleRoles, pageable)).thenReturn(Page.empty());

        userService.getAllUsers(pageable, "2025-9999", authorities("LIBRARIAN"));

        verify(repository).findByUserRoleIn(visibleRoles, pageable);
    }

    @Test
    void getUserByUniversityId_administratorCanReadFacultyProfile() {
        existingUser.setUserRole(UserRole.FACULTY);

        UserResponseDTO result = userService.getUserByUniversityId(
                "2025-9999", "2025-4321", authorities("ADMINISTRATOR"));

        assertEquals("2025-4321", result.getUniversityId());
    }

    @Test
    void getUserByUniversityId_librarianCanReadLibraryAssistantButAdministratorCannot() {
        existingUser.setUserRole(UserRole.ADMINISTRATOR);

        assertThrows(AccessDeniedException.class, () -> userService.getUserByUniversityId(
                "2025-9999", "2025-4321", authorities("ADMINISTRATOR")));

        UserResponseDTO result = userService.getUserByUniversityId(
                "2025-9999", "2025-4321", authorities("LIBRARIAN"));
        assertEquals("2025-4321", result.getUniversityId());
    }

    @Test
    void updateUser_librarianCanManageLibraryAssistantButAdministratorCannot() {
        existingUser.setUserRole(UserRole.ADMINISTRATOR);
        UserPatchRequestDTO request = new UserPatchRequestDTO("Updated", null, null, null);

        assertThrows(AccessDeniedException.class, () -> userService.updateUser(
                "2025-9999", "2025-4321", request, authorities("ADMINISTRATOR")));

        UserResponseDTO result = userService.updateUser(
                "2025-9999", "2025-4321", request, authorities("LIBRARIAN"));

        assertEquals("Updated", result.getFirstName());
    }

    @Test
    void deleteUser_studentCanDeleteOwnAccount() {
        userService.deleteUserByUniversityId("2025-4321", "2025-4321", authorities("STUDENT"));

        verify(repository).delete(existingUser);
    }

    @Test
    void deleteUser_librarianCanDeleteLibraryAssistantButAdministratorCannot() {
        existingUser.setUserRole(UserRole.ADMINISTRATOR);

        assertThrows(AccessDeniedException.class, () -> userService.deleteUserByUniversityId(
                "2025-9999", "2025-4321", authorities("ADMINISTRATOR")));

        userService.deleteUserByUniversityId("2025-9998", "2025-4321", authorities("LIBRARIAN"));

        verify(repository).delete(existingUser);
    }

    @Test
    void deleteUser_librarianCannotDeleteOwnAccount() {
        existingUser.setUserRole(UserRole.LIBRARIAN);

        assertThrows(AccessDeniedException.class, () -> userService.deleteUserByUniversityId(
                "2025-4321", "2025-4321", authorities("LIBRARIAN")));

        verify(repository, never()).delete(any(User.class));
    }

    @Test
    void deleteUser_administratorCannotDeleteOwnAccount() {
        existingUser.setUserRole(UserRole.ADMINISTRATOR);

        assertThrows(AccessDeniedException.class, () -> userService.deleteUserByUniversityId(
                "2025-4321", "2025-4321", authorities("ADMINISTRATOR")));

        verify(repository, never()).delete(any(User.class));
    }

    @Test
    void updatePassword_staffMustUseResetRoute() {
        existingUser.setUserRole(UserRole.ADMINISTRATOR);
        existingUser.setPasswordHash("old-hash");
        when(passwordEncoder.encode("NewPassword2@")).thenReturn("new-hash");
        PasswordChangeRequestDTO request = new PasswordChangeRequestDTO("Current1!", "NewPassword2@");

        assertThrows(AccessDeniedException.class, () -> userService.updatePassword(
                "2025-9999", "2025-4321", request, authorities("ADMINISTRATOR")));

        assertThrows(AccessDeniedException.class, () -> userService.updatePassword(
                "2025-9999", "2025-4321", request, authorities("LIBRARIAN")));
        assertEquals("old-hash", existingUser.getPasswordHash());
    }

    /** A permitted reset stores only an encoded password, raises the flag, and records one metric. */
    @Test
    void resetPassword_marksAccountForOwnerChange() {
        existingUser.setPasswordHash("old-hash");
        when(passwordEncoder.encode("Temporary1!")).thenReturn("new-hash");
        double before = meterRegistry.counter("libro.auth.password_resets").count();

        userService.resetPassword("2025-9999", "2025-4321",
                new PasswordResetRequestDTO("Temporary1!"), authorities("ADMINISTRATOR"));

        assertEquals("new-hash", existingUser.getPasswordHash());
        assertTrue(existingUser.isMustChangePassword());
        assertEquals(before + 1, meterRegistry.counter("libro.auth.password_resets").count());
        verify(repository, never()).save(any(User.class));
    }

    /** An assistant cannot reset a peer, even with a valid temporary password. */
    @Test
    void resetPassword_deniesOutOfScopeTarget() {
        existingUser.setUserRole(UserRole.ADMINISTRATOR);
        assertThrows(AccessDeniedException.class, () -> userService.resetPassword("2025-9999", "2025-4321",
                new PasswordResetRequestDTO("Temporary1!"), authorities("ADMINISTRATOR")));
        verify(passwordEncoder, never()).encode(anyString());
    }

    /** Multibyte passwords over the BCrypt byte boundary never reach the encoder. */
    @Test
    void resetPassword_rejectsOverlongUtf8() {
        assertThrows(app.global.exception.InvalidRequestException.class,
                () -> userService.resetPassword("2025-9999", "2025-4321",
                        new PasswordResetRequestDTO("Ä".repeat(35) + "Aa1!"), authorities("LIBRARIAN")));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void createAdministrator_usesFixedRoleAndNoAcademicFields() {
        UserCreateRequestDTO request = new UserCreateRequestDTO(
                "2025-8888", "ValidPass1!", "Library", "Assistant", null,
                "assistant@example.edu", UserRole.LIBRARIAN, null, null);
        when(repository.existsByUniversityId("2025-8888")).thenReturn(false);
        when(repository.existsByEmailIgnoreCase("assistant@example.edu")).thenReturn(false);
        when(passwordEncoder.encode("ValidPass1!")).thenReturn("encoded-password");
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO result = userService.createAdministrator(request, authorities("LIBRARIAN"));

        assertEquals(UserRole.ADMINISTRATOR, result.getUserRole());
        assertNull(result.getUserCourse());
        assertNull(result.getMajor());
    }

    @Test
    void publicSignup_ignoresRequestedPrivilegedRole() {
        UserCreateRequestDTO request = new UserCreateRequestDTO(
                "2025-8888", "ValidPass1!", "Student", "Patron", null,
                "student@example.edu", UserRole.LIBRARIAN, UserCourse.IT, UserITMajor.SE);
        when(passwordEncoder.encode("ValidPass1!")).thenReturn("encoded-password");
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO result = userService.createUser(request);

        assertEquals(UserRole.STUDENT, result.getUserRole());
    }

    /** Verifies signup rejects multibyte input that exceeds BCrypt's byte boundary. */
    @Test
    void signupRejectsPasswordOverBcryptByteLimitBeforePersistence() {
        String password = "Aa1!" + "Ä".repeat(35);
        UserCreateRequestDTO request = new UserCreateRequestDTO(
                "2025-8888", password, "Student", "Patron", null,
                "student@example.edu", UserRole.STUDENT, UserCourse.IT, UserITMajor.SE);

        assertThrows(IllegalArgumentException.class, () -> userService.createUser(request));

        verifyNoInteractions(repository, passwordEncoder);
    }

    @Test
    void createFaculty_usesFixedRoleForLibrarian() {
        UserCreateRequestDTO request = new UserCreateRequestDTO(
                "2025-8888", "ValidPass1!", "School", "Faculty", null,
                "faculty@example.edu", UserRole.ADMINISTRATOR, null, null);
        when(passwordEncoder.encode("ValidPass1!")).thenReturn("encoded-password");
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO result = userService.createFaculty(request, authorities("LIBRARIAN"));

        assertEquals(UserRole.FACULTY, result.getUserRole());
        assertNull(result.getUserCourse());
        assertNull(result.getMajor());
    }

    @Test
    void staffAccountCreation_requiresLibrarianAuthority() {
        UserCreateRequestDTO request = new UserCreateRequestDTO(
                "2025-8888", "ValidPass1!", "Library", "Assistant", null,
                "assistant@example.edu", UserRole.LIBRARIAN, null, null);

        assertThrows(AccessDeniedException.class,
                () -> userService.createFaculty(request, authorities("ADMINISTRATOR")));
        assertThrows(AccessDeniedException.class,
                () -> userService.createAdministrator(request, authorities("ADMINISTRATOR")));
        verifyNoInteractions(repository);
    }

    /** Verifies omitted fields remain unchanged during a partial update. */
    @Test
    void updateUser_shouldPreserveOmittedFields() {
        UserResponseDTO result = updateUser(
                "2025-4321",
                new UserPatchRequestDTO("New First", null, null, null)
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
                InvalidRequestException.class,
                () -> updateUser("2025-4321", null)
        );

        verifyNoInteractions(repository);
    }

    /** Verifies DTO constraints are evaluated for supplied update values. */
    @Test
    void updateUser_whenValueViolatesDtoConstraint_shouldReject() {
        String tooLongName = "x".repeat(51);

        assertThrows(
                ConstraintViolationException.class,
                () -> updateUser(
                        "2025-4321",
                        new UserPatchRequestDTO(tooLongName, null, null, null)
                )
        );

        verify(repository, never()).save(any(User.class));
    }

    /** Verifies blank names are rejected even though @Size permits blank text. */
    @Test
    void updateUser_whenNameIsBlank_shouldReject() {
        assertThrows(
                IllegalArgumentException.class,
                () -> updateUser(
                        "2025-4321",
                        new UserPatchRequestDTO("   ", null, null, null)
                )
        );
    }

    /** Verifies retaining the current email does not trigger a duplicate check. */
    @Test
    void updateUser_whenEmailIsUnchanged_shouldAllowIt() {
        UserResponseDTO result = updateUser(
                "2025-4321",
                new UserPatchRequestDTO(null, null, null, " OLD@EXAMPLE.COM ")
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
                () -> updateUser(
                        "2025-4321",
                        new UserPatchRequestDTO(null, null, null, "other@example.com")
                )
        );

        assertEquals("old@example.com", existingUser.getEmail());
    }

    /** Verifies a valid current password produces and stores a new encoded hash. */
    @Test
    void updatePassword_shouldVerifyAndEncodeNewPassword() {
        existingUser.setPasswordHash("old-hash");
        existingUser.setMustChangePassword(true);
        when(passwordEncoder.matches("Current1!", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword2@")).thenReturn("new-hash");

        updatePassword(
                "2025-4321",
                new PasswordChangeRequestDTO("Current1!", "NewPassword2@")
        );

        assertEquals("new-hash", existingUser.getPasswordHash());
        assertFalse(existingUser.isMustChangePassword());
        verify(passwordEncoder).matches("Current1!", "old-hash");
        verify(passwordEncoder).encode("NewPassword2@");
        verify(repository, never()).save(any(User.class));
    }

    /** Verifies an incorrect current password prevents the hash from changing. */
    @Test
    void updatePassword_whenCurrentPasswordIsIncorrect_shouldReject() {
        existingUser.setPasswordHash("old-hash");
        when(passwordEncoder.matches("Current1!", "old-hash")).thenReturn(false);

        assertEquals("PASSWORD_CURRENT_INCORRECT", assertThrows(
                InvalidRequestException.class,
                () -> updatePassword(
                        "2025-4321",
                        new PasswordChangeRequestDTO("Current1!", "NewPassword2@")
                )
        ).getCode());

        assertEquals("old-hash", existingUser.getPasswordHash());
        verify(passwordEncoder, never()).encode(anyString());
    }

    /** Verifies every password-complexity requirement is enforced before user lookup. */
    @Test
    void updatePassword_whenPasswordMissesComplexityRequirement_shouldReject() {
        String[] invalidPasswords = {
                "Short1!",
                "lowercase1!",
                "UPPERCASE1!",
                "NoNumber!",
                "NoSymbol1"
        };

        for (String invalidPassword : invalidPasswords) {
            assertThrows(
                    ConstraintViolationException.class,
                    () -> updatePassword(
                            "2025-4321",
                            new PasswordChangeRequestDTO("Current1!", invalidPassword)
                    ),
                    () -> "Expected password to be invalid: " + invalidPassword
            );
        }

        verify(repository, never()).findByUniversityId(anyString());
    }

    /** Verifies password changes enforce BCrypt's byte limit even for valid character counts. */
    @Test
    void updatePasswordRejectsMultibyteInputOverBcryptByteLimitBeforeLookup() {
        String password = "Aa1!" + "Ä".repeat(35);

        assertThrows(IllegalArgumentException.class,
                () -> updatePassword("2025-4321", new PasswordChangeRequestDTO("Current1!", password)));

        verifyNoInteractions(repository, passwordEncoder);
    }

    /** Verifies a missing user prevents password verification and encoding. */
    @Test
    void updatePassword_whenUserDoesNotExist_shouldReject() {
        reset(repository);
        when(repository.findByUniversityId("2025-9999"))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> updatePassword(
                        "2025-9999",
                        new PasswordChangeRequestDTO("Current1!", "NewPassword2@")
                )
        );

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(passwordEncoder, never()).encode(anyString());
    }

    /** Verifies a null password request is rejected before repository access. */
    @Test
    void updatePassword_whenRequestIsNull_shouldReject() {
        assertThrows(
                IllegalArgumentException.class,
                () -> updatePassword("2025-4321", null)
        );

        verifyNoInteractions(repository);
    }

    /** Verifies account creation rejects passwords that miss a complexity requirement. */
    @Test
    void createUser_whenPasswordMissesSymbol_shouldRejectBeforeRepositoryAccess() {
        UserCreateRequestDTO request = new UserCreateRequestDTO(
                "2025-1234",
                "NoSymbol1",
                "Jane",
                "Doe",
                null,
                "jane@example.com",
                UserRole.STUDENT,
                UserCourse.IT,
                UserITMajor.SE
        );

        assertThrows(
                ConstraintViolationException.class,
                () -> userService.createUser(request)
        );

        verifyNoInteractions(repository);
        verifyNoInteractions(passwordEncoder);
    }

    /** Adapts profile-update tests to the authenticated-owner service contract. */
    private UserResponseDTO updateUser(String targetId, UserPatchRequestDTO request) {
        return userService.updateUser(targetId, targetId, request, authorities("STUDENT"));
    }

    /** Adapts password tests to the authenticated-owner service contract. */
    private void updatePassword(String targetId, PasswordChangeRequestDTO request) {
        userService.updatePassword(targetId, targetId, request, authorities("STUDENT"));
    }

    /** Exercises the student directory as an authorized administrator. */
    private Page<UserResponseDTO> getAllUsers(Pageable pageable) {
        return userService.getAllUsers(pageable, "2025-4321", authorities("ADMINISTRATOR"));
    }

    /** Builds one Spring Security role authority for a service call. */
    private Collection<? extends GrantedAuthority> authorities(String role) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    /** Verifies overly long passwords are rejected before BCrypt processing. */
    @Test
    void createUser_whenPasswordExceedsMaximumLength_shouldRejectBeforeHashing() {
        String overlyLongPassword = "A" + "a".repeat(70) + "1!";
        UserCreateRequestDTO request = new UserCreateRequestDTO(
                "2025-1234",
                overlyLongPassword,
                "Jane",
                "Doe",
                null,
                "jane@example.com",
                UserRole.STUDENT,
                UserCourse.IT,
                UserITMajor.SE
        );

        assertThrows(ConstraintViolationException.class, () -> userService.createUser(request));
        verifyNoInteractions(repository);
        verifyNoInteractions(passwordEncoder);
    }
}
