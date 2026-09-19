package app.user.service;

import app.user.dto.UserCreateRequestDTO;
import app.user.dto.UserCreateUpdateDTO;
import app.user.dto.UserResponseDTO;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final Validator validator;

    /**
     * Validates and persists a new user.
     *
     * <p>The supplied four-digit password is encoded before persistence. The
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
        validateAcademicRules(request);

        // encodes password using bcrypt
        String encodedPasswordHash = passwordEncoder.encode(request.getPassword());
        User user = userMapper.toEntity(request, encodedPasswordHash);

        // persist the normalized values, not the original client input.
        user.setUniversityId(universityId);
        user.setEmail(email);

        // save user
        User savedUser = userRepository.save(user);
        return userMapper.toResponseDTO(savedUser);
    }

    /**
     * Applies a validated partial update to an existing user.
     *
     * <p>{@code null} fields mean that the caller omitted the field and the
     * current entity value is preserved. Supplied values are trimmed,
     * normalized, validated, and applied to the managed entity. Hibernate
     * dirty checking persists the changes when the transaction commits.</p>
     *
     * @param universityId the university ID of the user being updated
     * @param request the partial update request
     * @return the updated user without password data
     * @throws IllegalArgumentException if the request or a supplied value is invalid
     * @throws UserNotFoundException if no user matches the university ID
     */
    @Transactional
    public UserResponseDTO updateUser(
            String universityId,
            UserCreateUpdateDTO request
    ) {
        // Reject null input before reading request fields.
        if (request == null) {
            throw new IllegalArgumentException("User update request cannot be null");
        }

        // Normalize and validate the lookup ID before querying the repository.
        String normalizedUniversityId =
                normalizeAndValidateUniversityId(universityId);

        // Load the managed entity so Hibernate can track field changes.
        User user = userRepository.findByUniversityId(normalizedUniversityId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Normalize supplied values without mutating the input DTO.
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
     * Returns a page of users converted to safe response DTOs.
     *
     * @param pageable page number, page size, and optional sorting rules
     * @return a page containing user response DTOs
     * @throws IllegalArgumentException if pageable is null
     */
    public Page<UserResponseDTO> getAllUsers(Pageable pageable) {
        if (pageable == null) {
            throw new IllegalArgumentException("Pageable cannot be null");
        }

        return userRepository.findAll(pageable)
                .map(userMapper::toResponseDTO);
    }

    /**
     * Finds a user by the public university ID.
     *
     * @param universityId the university ID to search for
     * @return the matching user without password data
     * @throws UserNotFoundException if no matching user exists
     * @throws IllegalArgumentException if the university ID format is invalid
     */
    public UserResponseDTO getUserByUniversityId(String universityId) {
        String normalizedUniversityId = normalizeAndValidateUniversityId(universityId);

        User user = userRepository.findByUniversityId(normalizedUniversityId)
                .orElseThrow(() -> new UserNotFoundException(normalizedUniversityId));

        return userMapper.toResponseDTO(user);
    }

    /**
     * Deletes a user identified by the public university ID.
     *
     * @param universityId the university ID of the user to delete
     * @throws UserNotFoundException if no matching user exists
     * @throws IllegalArgumentException if the university ID format is invalid
     */
    @Transactional
    public void deleteUserByUniversityId(String universityId) {
        String normalizedUniversityId = normalizeAndValidateUniversityId(universityId);
        log.debug("Attempting to delete user with university ID: {}", normalizedUniversityId);

        User user = userRepository.findByUniversityId(normalizedUniversityId)
                .orElseThrow(() -> new UserNotFoundException(normalizedUniversityId));

        userRepository.delete(user);
    }

    /**
     * Trims and validates a university ID using the database and DTO format.
     */
    private String normalizeAndValidateUniversityId(String universityId) {
        // could either be normalized email or null
        String normalizedUniversityId = universityId == null
                ? null
                : universityId.trim();

        if (normalizedUniversityId == null || normalizedUniversityId.isBlank()) {
            throw new IllegalArgumentException("University ID cannot be null or blank");
        }

        // checks id if it follows the required pattern
        if (!normalizedUniversityId.matches("\\d{4}-\\d{4}")) {
            throw new IllegalArgumentException(
                    "University ID must follow this format: 2025-4321"
            );
        }

        return normalizedUniversityId;
    }



    /**
     * Enforces the relationship between role, course, and IT major.
     */
    private void validateAcademicRules(UserCreateRequestDTO request) {
        UserRole role = request.getUserRole();
        UserCourse course = request.getUserCourse();

        if (role == null) {
            throw new IllegalArgumentException("User role cannot be null");
        }

        if (role == UserRole.FACULTY) {
            if (course != null || request.getMajor() != null) {
                throw new IllegalArgumentException(
                        "Faculty members cannot have a course or a major"
                );
            }
            return;
        }

        if (course == null) {
            throw new IllegalArgumentException("Students must have a course");
        }

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
     */
    private String normalizeAndValidateEmail(String email) {
        // could either be normalized email or null
        String normalizedEmail = email == null
                ? null
                : email.trim().toLowerCase(Locale.ROOT);

        // validates if null or blank
        validateRequiredValue(normalizedEmail, "Email");

        return normalizedEmail;
    }

    /**
     * Ensures values required by the persistence model are not blank when the
     * service is called directly without controller validation.
     */
    private void validateRequiredValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
    }
}
