package app.user.service;

import app.user.dto.UserCreateRequestDTO;
import app.user.dto.UserCreateUpdateDTO;
import app.user.dto.UserReplaceRequest;
import app.user.dto.UserResponseDTO;
import app.user.dto.ChangePasswordDTO;
import app.user.entity.User;
import app.user.entity.enums.UserCourse;
import app.user.entity.enums.UserRole;
import app.user.exception.UserNotFoundException;
import app.user.mapper.UserMapper;
import app.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Coordinates user business rules, persistence, password hashing, and DTO
 * conversion.
 *
 * <p>This service is the boundary between the user feature's controller and
 * repository. Controllers should pass request DTOs to this class and receive
 * response DTOs rather than JPA entities.</p>
 */
@Slf4j
@Service
@AllArgsConstructor
public class UserService {

    private static final Set<String> SORTABLE_USER_FIELDS = Set.of(
            "universityId", "firstName", "lastName", "middleInitial", "email", "userRole", "userCourse", "major"
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final Validator validator;

    /**
     * Validates and persists a new user.
     *
     * <p>The supplied password is encoded before persistence. The
     * raw password is never assigned to the entity or returned to the caller.
     * University IDs and email addresses are trimmed before duplicate checks
     * and storage.</p>
     *
     * @param request the user creation request
     * @return the saved user without password data
     * @throws IllegalArgumentException if the request or a business rule is invalid
     */
    @Transactional
    public UserResponseDTO createUser(UserCreateRequestDTO request) {
        return createUserWithRole(request, UserRole.STUDENT);
    }

    /**
     * Creates a faculty account; the controller's librarian-only route guard controls access.
     *
     * @param request faculty account data; the requested role is ignored
     * @return the created faculty profile without password data
     */
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Transactional
    public UserResponseDTO createFaculty(UserCreateRequestDTO request) {
        return createUserWithRole(request, UserRole.FACULTY);
    }

    /**
     * Validates, normalizes, hashes, and stores an account with the service-selected role.
     *
     * @param request account details supplied by the caller
     * @param role role selected by this service method, never trusted from the request body
     * @return the saved public profile without password data
     */
    private UserResponseDTO createUserWithRole(UserCreateRequestDTO request, UserRole role) {
        if (request == null) {
            throw new IllegalArgumentException("User request cannot be null");
        }

        // validate request
        validateRequest(request);

        // normalize / validate university id
        String universityId = normalizeAndValidateUniversityId(request.getUniversityId());

        // normalize / valid email
        String email = normalizeAndValidateEmail(request.getEmail());

        // normalize / validate required values
        validateRequiredValue(request.getPassword(), "Password");

        // check if university id is already used within the system
        if (userRepository.existsByUniversityId(universityId)) {
            throw new IllegalArgumentException("University ID is already registered");
        }

        // check if email is already used within the system
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        // enforces the relationship between role, course, and IT major
        validateAcademicRules(request, role);

        // encodes password using bcrypt
        String encodedPasswordHash = passwordEncoder.encode(request.getPassword());
        User user = userMapper.toEntity(request, encodedPasswordHash);
        user.setUserRole(role);
        if (role == UserRole.FACULTY) {
            user.setUserCourse(null);
            user.setMajor(null);
        }

        // persist the normalized values, not the original client input.
        user.setUniversityId(universityId);
        user.setEmail(email);

        // save user
        User savedUser = userRepository.save(user);
        return userMapper.toResponseDTO(savedUser);
    }

    /**
     * Replaces the editable profile fields of the target account.
     *
     * <p>The owner may replace their own profile. Administrators and librarians
     * may also manage student and faculty profiles, but not another administrator
     * or librarian profile.
     * This method changes only first name, last name, middle initial, and email.
     * The managed entity is persisted by Hibernate dirty checking at commit.</p>
     *
     * @param callerId university ID of the authenticated caller
     * @param targetId university ID identifying the account to update
     * @param request complete replacement profile fields
     * @param authorities roles granted to the authenticated caller
     * @return the updated public profile without password data
     * @throws AccessDeniedException if the caller cannot manage the target account
     * @throws IllegalArgumentException if the request or profile values are invalid
     * @throws UserNotFoundException if the target account does not exist
     */
    @Transactional
    public UserResponseDTO replaceUserProfile(
            String callerId,
            String targetId,
            UserReplaceRequest request,
            Collection<? extends GrantedAuthority> authorities
    ) {
        // Reject a missing body before reading any of its fields.
        if (request == null) {
            throw new IllegalArgumentException("User profile cannot be null");
        }

        // Normalize both IDs so the owner comparison uses the same format as storage.
        String normalizedCallerId = normalizeAndValidateUniversityId(callerId);
        String normalizedTargetId = normalizeAndValidateUniversityId(targetId);

        // Load the target account whose profile is being replaced.
        User user = userRepository.findByUniversityId(normalizedTargetId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Check ownership or staff permission before exposing or changing profile data.
        requireAccountAccess(normalizedCallerId, normalizedTargetId, user, authorities);

        // Require every field for this full replacement and enforce DTO constraints.
        validateRequest(request);

        // Trim names and normalize the email before storing them.
        String firstName = request.getFirstName().trim();
        String lastName = request.getLastName().trim();
        String middleInitial = request.getMiddleInitial() == null
                ? null
                : request.getMiddleInitial().trim();
        String email = normalizeAndValidateEmail(request.getEmail());

        // Treat a blank middle initial as absent in the profile.
        if (middleInitial != null && middleInitial.isBlank()) {
            middleInitial = null;
        }

        // An unchanged email is valid; a different email must be unique.
        if (!email.equals(user.getEmail())
                && userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        // Replace only profile fields; university ID and account fields stay fixed.
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setMiddleInitial(middleInitial);
        user.setEmail(email);
        // Return the public DTO and let transaction commit persist dirty changes.
        return userMapper.toResponseDTO(user);
    }

    /**
     * Applies a validated partial profile update to the target account.
     *
     * <p>The owner may update their own profile. Administrators and librarians
     * may also manage student and faculty profiles. {@code null} fields remain unchanged;
     * supplied values are normalized and validated before assignment.</p>
     *
     * @param callerId university ID of the authenticated caller
     * @param targetId university ID identifying the account to update
     * @param request fields supplied for the partial update
     * @param authorities roles granted to the authenticated caller
     * @return the updated public profile without password data
     * @throws AccessDeniedException if the caller cannot manage the target account
     * @throws IllegalArgumentException if the request or a supplied value is invalid
     * @throws UserNotFoundException if the target account does not exist
     */
    @Transactional
    public UserResponseDTO updateUser(
            String callerId,
            String targetId,
            UserCreateUpdateDTO request,
            Collection<? extends GrantedAuthority> authorities
    ) {
        // Reject a missing request before reading any supplied fields.
        if (request == null) {
            throw new IllegalArgumentException("User update request cannot be null");
        }

        // Normalize both IDs so authorization compares canonical university IDs.
        String normalizedCallerId = normalizeAndValidateUniversityId(callerId);
        String normalizedTargetId = normalizeAndValidateUniversityId(targetId);

        // Load the account named by the target ID in the route.
        User user = userRepository.findByUniversityId(normalizedTargetId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Prevent a caller from changing another account without owner or staff permission.
        requireAccountAccess(normalizedCallerId, normalizedTargetId, user, authorities);

        // Normalize supplied values without changing the request object.
        UserCreateUpdateDTO normalizedRequest = new UserCreateUpdateDTO(
                trimNullable(request.getFirstName()),
                trimNullable(request.getLastName()),
                normalizeMiddleInitial(request.getMiddleInitial()),
                normalizeNullableEmail(request.getEmail())
        );

        // Apply DTO constraints for size, email format, and middle-initial format.
        validateRequest(normalizedRequest);

        // Reject blank names because @Size alone permits blank strings.
        validateIfPresent(normalizedRequest.getFirstName(), "First name");
        validateIfPresent(normalizedRequest.getLastName(), "Last name");

        // Reject blank email input because @Email permits an empty value.
        validateIfPresent(normalizedRequest.getEmail(), "Email");

        // Reuse the normalized email for conflict checking and assignment.
        String newEmail = normalizedRequest.getEmail();

        // Check uniqueness only when the email is actually changing.
        if (newEmail != null
                && !newEmail.equals(user.getEmail())
                && userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        // Apply only supplied fields.
        if (normalizedRequest.getFirstName() != null) {
            user.setFirstName(normalizedRequest.getFirstName());
        }

        // Preserve the current last name when it was omitted.
        if (normalizedRequest.getLastName() != null) {
            user.setLastName(normalizedRequest.getLastName());
        }

        // Preserve the current middle initial when it was omitted or blank.
        if (normalizedRequest.getMiddleInitial() != null) {
            user.setMiddleInitial(normalizedRequest.getMiddleInitial());
        }

        // Preserve the current email when it was omitted.
        if (newEmail != null) {
            user.setEmail(newEmail);
        }

        // Return a response DTO; dirty checking persists the managed entity.
        return userMapper.toResponseDTO(user);
    }

    /**
     * Changes the password for the target account.
     *
     * <p>Owners must provide their current password. Administrators and
     * librarians may reset a student or faculty password without knowing the target user's
     * current password. Passwords are validated and only the encoded new hash
     * is stored; transaction commit persists the managed entity.</p>
     *
     * @param callerId university ID of the authenticated caller
     * @param targetId university ID identifying the account whose password changes
     * @param request current password for an owner change and the new password
     * @param authorities roles granted to the authenticated caller
     * @throws AccessDeniedException if the caller cannot manage the target account
     * @throws IllegalArgumentException if the request is missing or the current password is incorrect
     * @throws UserNotFoundException if the target account does not exist
     * @throws ConstraintViolationException if the new password violates policy
     */
    @Transactional
    public void updatePassword(
            String callerId,
            String targetId,
            ChangePasswordDTO request,
            Collection<? extends GrantedAuthority> authorities
    ) {
        // Reject a missing body before reading either password.
        if (request == null) {
            throw new IllegalArgumentException("Password update request cannot be null");
        }

        // Normalize caller and target IDs before checking ownership.
        String normalizedCallerId = normalizeAndValidateUniversityId(callerId);
        String normalizedTargetId = normalizeAndValidateUniversityId(targetId);

        // Load the account whose password hash may be replaced.
        User user = userRepository.findByUniversityId(normalizedTargetId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Check authorization before processing the supplied password values.
        requireAccountAccess(normalizedCallerId, normalizedTargetId, user, authorities);

        // Always validate the new password; the current password is optional for staff resets.
        validateRequest(request);

        // Require a current password when the caller is changing their own password.
        if (normalizedCallerId.equals(normalizedTargetId)) {
            validateRequiredValue(request.getCurrentPassword(), "Current password");

            // Verify the owner's current password against the stored BCrypt hash.
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
        }

        // Encode the new password before assigning it to the entity.
        String encodedPasswordHash =
                passwordEncoder.encode(request.getNewPassword());

        // Replace only the stored hash; raw password values never reach the entity.
        user.setPasswordHash(encodedPasswordHash);
    }

    /**
     * Validates any request DTO with Jakarta Bean Validation.
     *
     * @param request the DTO to validate
     * @param <T> the DTO type
     * @throws ConstraintViolationException when one or more constraints fail
     */
    private <T> void validateRequest(T request) {
        // Collect every violation so callers receive the complete result.
        Set<ConstraintViolation<T>> violations = validator.validate(request);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    /**
     * Trims an optional update value while preserving {@code null} as omission.
     *
     * @param value the optional value
     * @return the trimmed value, or {@code null}
     */
    private String trimNullable(String value) {
        // Preserve null as omission instead of converting it to an empty string.
        return value == null ? null : value.trim();
    }

    /**
     * Normalizes an optional email using the same lowercase rule as creation.
     *
     * @param email the optional email
     * @return the normalized email, or {@code null}
     */
    private String normalizeNullableEmail(String email) {
        // Preserve null so an omitted email does not overwrite the current value.
        return normalizeEmail(email);
    }

    /**
     * Trims and lowercases an email using a locale-independent rule.
     *
     * @param email the email value, or {@code null}
     * @return the normalized email, or {@code null}
     */
    private String normalizeEmail(String email) {
        // Use a stable locale for lowercase conversion and preserve null input.
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Trims an optional middle initial and treats blank input as omitted.
     *
     * @param middleInitial the optional middle initial
     * @return the trimmed middle initial, or {@code null}
     */
    private String normalizeMiddleInitial(String middleInitial) {
        // Preserve null as omission.
        if (middleInitial == null) {
            return null;
        }

        // Trim whitespace before applying the one-letter constraint.
        String normalized = middleInitial.trim();

        // Treat empty or whitespace-only input as omitted.
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Rejects blank values when a partial-update field was explicitly supplied.
     *
     * @param value the optional field value
     * @param fieldName the human-readable field name
     */
    private void validateIfPresent(String value, String fieldName) {
        // Treat null as omission; reject only supplied blank values.
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
    }

    /**
     * Returns a page of student profiles for an administrator or librarian.
     *
     * @param pageable page number, page size, and optional sorting rules
     * @param callerId university ID of the authenticated caller
     * @param authorities roles granted to the authenticated caller
     * @return a page containing student response DTOs only
     * @throws AccessDeniedException if the caller is not an administrator or librarian
     * @throws IllegalArgumentException if pageable is null or contains an unsupported sort field
     */
    public Page<UserResponseDTO> getAllUsers(
            Pageable pageable,
            String callerId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        // Enforce the directory boundary even if a service caller bypasses SecurityConfig.
        requireStaff(callerId, authorities);

        // Guard service callers that bypass Spring MVC pagination binding.
        if (pageable == null) {
            throw new IllegalArgumentException("Pageable cannot be null");
        }

        // Restrict sort properties so client input cannot select arbitrary entity fields.
        if (pageable.getSort().stream().anyMatch(order -> !SORTABLE_USER_FIELDS.contains(order.getProperty()))) {
            throw new IllegalArgumentException("Unsupported user sort field. Allowed fields: universityId, firstName, lastName, middleInitial, email, userRole, userCourse, major");
        }

        // Query only student records and map them into safe public response DTOs.
        return userRepository.findByUserRole(UserRole.STUDENT, pageable)
                .map(userMapper::toResponseDTO);
    }

    /**
     * Finds a user profile after checking owner or staff access.
     *
     * @param callerId university ID of the authenticated caller
     * @param targetId university ID identifying the requested account
     * @param authorities roles granted to the authenticated caller
     * @return the matching user without password data
     * @throws AccessDeniedException if the caller cannot view the target account
     * @throws UserNotFoundException if no matching user exists
     * @throws IllegalArgumentException if the university ID format is invalid
     */
    public UserResponseDTO getUserByUniversityId(
            String callerId,
            String targetId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        // Normalize the caller and target identifiers before comparing or querying.
        String normalizedCallerId = normalizeAndValidateUniversityId(callerId);
        String normalizedTargetId = normalizeAndValidateUniversityId(targetId);

        // Load the requested profile using the target ID from the route.
        User user = userRepository.findByUniversityId(normalizedTargetId)
                .orElseThrow(() -> new UserNotFoundException(normalizedTargetId));

        // A non-owner may read only student profiles, even when the caller is staff.
        requireProfileReadAccess(normalizedCallerId, normalizedTargetId, user, authorities);

        // Keep the persistence entity inside the service boundary.
        return userMapper.toResponseDTO(user);
    }

    /**
     * Deletes a student or faculty account for an administrator or librarian.
     *
     * @param callerId university ID of the authenticated caller
     * @param targetId university ID identifying the account to delete
     * @param authorities roles granted to the authenticated caller
     * @throws UserNotFoundException if no matching user exists
     * @throws AccessDeniedException if the caller is not staff or the target is protected
     * @throws IllegalArgumentException if the university ID format is invalid
     */
    @Transactional
    public void deleteUserByUniversityId(
            String callerId,
            String targetId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        // Require an administrator or librarian before looking up a deletion target.
        requireStaff(callerId, authorities);

        // Normalize the target ID before querying or including it in diagnostic logs.
        String normalizedTargetId = normalizeAndValidateUniversityId(targetId);
        log.debug("Attempting to delete user with university ID: {}", normalizedTargetId);

        // Load the account so its role can be checked before deletion.
        User user = userRepository.findByUniversityId(normalizedTargetId)
                .orElseThrow(() -> new UserNotFoundException(normalizedTargetId));

        // Never allow this endpoint to delete administrator or librarian accounts.
        if (user.getUserRole() != UserRole.STUDENT && user.getUserRole() != UserRole.FACULTY) {
            throw new AccessDeniedException("Access denied");
        }

        // Delete the managed entity within this transaction.
        userRepository.delete(user);
    }

    /**
     * Trims and validates a university ID using the API's required format.
     *
     * @param universityId the supplied university ID
     * @return the trimmed university ID
     * @throws IllegalArgumentException if the ID is blank or malformed
     */
    private String normalizeAndValidateUniversityId(String universityId) {
        // could either be normalized email or null
        String normalizedUniversityId = universityId == null
                ? null
                : universityId.trim();

        if (normalizedUniversityId == null || normalizedUniversityId.isBlank()) {
            throw new IllegalArgumentException("University ID cannot be null or blank");
        }

        // Enforce the expected four-digit, dash, four-digit format.
        if (!normalizedUniversityId.matches("\\d{4}-\\d{4}")) {
            throw new IllegalArgumentException(
                    "University ID must follow this format: 2025-4321"
            );
        }

        return normalizedUniversityId;
    }

    /**
     * Confirms that the caller is the owner or authorized staff for the target.
     *
     * <p>Every profile update and password operation calls this after loading its target.
     * Owners may manage their own account. Administrators and librarians may
     * manage student and faculty accounts, but not another administrator or librarian.</p>
     *
     * @param callerId normalized university ID of the authenticated caller
     * @param targetId normalized university ID requested by the caller
     * @param target loaded target user whose role determines staff boundaries
     * @param authorities authorities granted to the authenticated caller
     * @throws AccessDeniedException if no ownership or staff rule permits access
     */
    private void requireAccountAccess(
            String callerId,
            String targetId,
            User target,
            Collection<? extends GrantedAuthority> authorities
    ) {
        // The account owner can manage their own profile and password regardless of role.
        if (callerId.equals(targetId)) {
            return;
        }

        // Staff may manage student and faculty accounts, but not other staff accounts.
        if ((target.getUserRole() == UserRole.STUDENT || target.getUserRole() == UserRole.FACULTY)
                && isStaff(authorities)) {
            return;
        }

        // Reject cross-account requests that do not meet the rules above.
        throw new AccessDeniedException("Access denied");
    }

    /**
     * Confirms that the caller may read the requested public profile.
     *
     * <p>Every role may read its own profile. Administrators and librarians may
     * also read student profiles, but may not browse faculty or staff profiles.</p>
     *
     * @param callerId normalized university ID of the authenticated caller
     * @param targetId normalized university ID requested by the caller
     * @param target loaded account whose role defines directory visibility
     * @param authorities authorities granted to the authenticated caller
     * @throws AccessDeniedException if the caller cannot read the target profile
     */
    private void requireProfileReadAccess(
            String callerId,
            String targetId,
            User target,
            Collection<? extends GrantedAuthority> authorities
    ) {
        // The owner can always read their own profile.
        if (callerId.equals(targetId)) {
            return;
        }

        // Only staff can read another user's profile, and the directory contains students only.
        if (target.getUserRole() == UserRole.STUDENT && isStaff(authorities)) {
            return;
        }

        // Deny cross-account reads outside the student-directory boundary.
        throw new AccessDeniedException("Access denied");
    }

    /**
     * Requires the caller to hold the administrator or librarian authority.
     *
     * @param callerId university ID of the authenticated caller, for diagnostic context
     * @param authorities authorities granted to the authenticated caller
     * @throws AccessDeniedException if the caller does not hold either staff role
     */
    private void requireStaff(
            String callerId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        // Reject a missing or malformed authenticated identity before checking its role.
        normalizeAndValidateUniversityId(callerId);

        // Require one of the two staff authorities for directory and deletion operations.
        if (!isStaff(authorities)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    /**
     * Checks whether the supplied authorities include a staff role.
     *
     * @param authorities roles granted to the authenticated caller
     * @return {@code true} for administrators or librarians, otherwise {@code false}
     */
    private boolean isStaff(Collection<? extends GrantedAuthority> authorities) {
        // Treat a missing authority collection as no privileges.
        if (authorities == null) {
            return false;
        }

        // Compare exact Spring Security authority names rather than role substrings.
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ROLE_ADMINISTRATOR".equals(authority)
                        || "ROLE_LIBRARIAN".equals(authority));
    }

    /**
     * Enforces the relationship between role, course, and IT major.
     *
     * @param request the user creation request containing academic fields
     * @throws IllegalArgumentException if the role, course, and major combination is invalid
     */
    private void validateAcademicRules(UserCreateRequestDTO request, UserRole role) {
        UserCourse course = request.getUserCourse();

        if (role == UserRole.FACULTY) {
            // Faculty users cannot be assigned student academic details.
            if (course != null || request.getMajor() != null) {
                throw new IllegalArgumentException(
                        "Faculty members cannot have a course or a major"
                );
            }
            return;
        }

        if (course == null) {
            // Students must belong to a course.
            throw new IllegalArgumentException("Students must have a course");
        }

        // A major applies only to IT students, and IT students must choose one.
        if ((course == UserCourse.EMC || course == UserCourse.IS)
                && request.getMajor() != null) {
            throw new IllegalArgumentException("Only IT students can have majors");
        }

        if (course == UserCourse.IT && request.getMajor() == null) {
            throw new IllegalArgumentException("IT students must have a major");
        }
    }


    /**
     * Trims, lowercases, and validates an email using a locale-independent
     * normalization rule.
     *
     * @param email the supplied email address
     * @return the normalized email address
     * @throws IllegalArgumentException if the email is blank
     */
    private String normalizeAndValidateEmail(String email) {
        // Normalize before checking required-value rules.
        String normalizedEmail = normalizeEmail(email);

        // validates if null or blank
        validateRequiredValue(normalizedEmail, "Email");

        return normalizedEmail;
    }

    /**
     * Ensures values required by the persistence model are not blank when the
     * service is called directly without controller validation.
     *
     * @param value the value to check
     * @param fieldName the field's human-readable name for the error message
     * @throws IllegalArgumentException if the value is null or blank
     */
    private void validateRequiredValue(String value, String fieldName) {
        // Keep required-field checks active for internal service callers too.
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
    }
}
