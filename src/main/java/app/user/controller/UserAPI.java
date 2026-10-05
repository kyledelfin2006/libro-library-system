package app.user.controller;

import app.global.responses.ApiResponse;
import app.global.config.ErrorApiResponse;
import app.user.dto.ChangePasswordDTO;
import app.user.dto.UserCreateRequestDTO;
import app.user.dto.UserCreateUpdateDTO;
import app.user.dto.UserReplaceRequest;
import app.user.dto.UserResponseDTO;
import app.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for user account operations.
 *
 * <p>This controller handles request binding, validation, and HTTP response
 * shapes, then delegates account rules to {@link UserService}. OpenAPI
 * annotations describe the public endpoint contract.</p>
 */
@RestController
@RequestMapping("/app/users")
@Tag(name = "Users", description = "User account and profile operations. The current development security configuration permits unauthenticated access; do not expose these routes to untrusted clients.")
public class UserAPI {

    private final UserService service;

    /**
     * Creates the controller with its user-domain service.
     *
     * @param service user account operations
     */
    public UserAPI(UserService service) {
        this.service = service;
    }

    /**
     * Creates an account after validating the request body.
     *
     * @param request account data supplied by the client
     * @return HTTP 201 with the created public profile in a success envelope
     */
    @PostMapping
    @Operation(summary = "Create a user account",
            description = "Creates an account. Students must provide a course; IT students must provide an approved major; faculty must leave course and major null. Passwords must be 8–72 characters and include uppercase and lowercase letters, a number, and a symbol. Names are trimmed and email is stored lowercase.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User created")
    @ErrorApiResponse(responseCode = "400", description = "Request validation failed or university ID/email is already registered")
    @ErrorApiResponse(responseCode = "409", description = "A database uniqueness constraint was concurrently violated")
    public ResponseEntity<ApiResponse<UserResponseDTO>> createUser(
            @Valid @RequestBody UserCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "User created successfully", service.createUser(request)));
    }

    /**
     * Lists public user profiles using Spring's bound pagination parameters.
     *
     * @param pageable requested page, size, and sort order
     * @return the requested page of public profiles
     */
    @GetMapping
    @Operation(summary = "List users with pagination",
            description = "Page numbering starts at zero. The default is page 0, size 12, sorted by universityId ascending; the maximum page size is 100. Sort with sort=property,direction using universityId, firstName, lastName, middleInitial, email, userRole, userCourse, or major. Unsupported sort properties return 400.")
    @ErrorApiResponse(responseCode = "400", description = "Invalid pagination or sort parameter")
    public ResponseEntity<Page<UserResponseDTO>> getAllUsers(
            @ParameterObject @PageableDefault(size = 12, sort = "universityId") Pageable pageable) {
        return ResponseEntity.ok(service.getAllUsers(pageable));
    }

    /**
     * Retrieves one public profile without wrapping it in {@link ApiResponse}.
     *
     * @param universityId path identifier of the user
     * @return the matching public profile
     */
    @GetMapping("/{universityId}")
    @Operation(summary = "Get a user by university ID",
            description = "Returns the public profile fields. Passwords and password hashes are never included.")
    @ErrorApiResponse(responseCode = "400", description = "University ID must use the ####-#### format")
    @ErrorApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserResponseDTO> getUserByUniversityId(
            @Parameter(description = "University identifier in ####-#### format.", example = "2025-4321")
            @PathVariable String universityId) {
        return ResponseEntity.ok(service.getUserByUniversityId(universityId));
    }

    /**
     * Applies the supplied profile fields and returns the updated profile in an envelope.
     *
     * @param universityId path identifier of the user to update
     * @param request fields supplied for the partial update
     * @return the updated public profile in a success envelope
     */
    @PatchMapping("/{universityId}")
    @Operation(summary = "Update supplied profile fields",
            description = "Updates only supplied fields; null or omitted fields are unchanged. Supplied names and email cannot be blank. A blank middle initial is treated as omitted. Values are trimmed and email is stored lowercase.")
    @ErrorApiResponse(responseCode = "400", description = "Invalid profile field, university ID, or email is already registered")
    @ErrorApiResponse(responseCode = "404", description = "User not found")
    @ErrorApiResponse(responseCode = "409", description = "A database uniqueness constraint was concurrently violated")
    public ResponseEntity<ApiResponse<UserResponseDTO>> updateUser(
            @Parameter(description = "University identifier in ####-#### format.", example = "2025-4321")
            @PathVariable String universityId,
            @RequestBody UserCreateUpdateDTO request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "User updated successfully",
                service.updateUser(universityId, request)));
    }

    /**
     * Replaces the editable profile fields and returns the result in an envelope.
     *
     * @param universityId path identifier of the user to replace
     * @param request complete replacement for the editable profile fields
     * @return the updated public profile in a success envelope
     */
    @PutMapping("/{universityId}")
    @Operation(summary = "Replace a user's profile",
            description = "Replaces first name, last name, middle initial, and email. First name, last name, and email are required; university ID, role, course, major, and password are not changed. Names are trimmed and email is stored lowercase.")
    @ErrorApiResponse(responseCode = "400", description = "Request validation failed, university ID is invalid, or email is already registered")
    @ErrorApiResponse(responseCode = "404", description = "User not found")
    @ErrorApiResponse(responseCode = "409", description = "A database uniqueness constraint was concurrently violated")
    public ResponseEntity<ApiResponse<UserResponseDTO>> replaceUserProfile(
            @Parameter(description = "University identifier in ####-#### format.", example = "2025-4321")
            @PathVariable String universityId,
            @Valid @RequestBody UserReplaceRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "User profile replaced successfully",
                service.replaceUserProfile(universityId, request)));
    }

    /**
     * Delegates password verification and update without returning password data.
     *
     * @param universityId path identifier of the user whose password changes
     * @param request current and replacement password values
     * @return an empty success envelope when the password is updated
     */
    @PutMapping("/{universityId}/password")
    @Operation(summary = "Change a user's password",
            description = "Verifies the current password before storing the new password hash. Both passwords must meet the 8–72 character complexity policy; password values are never returned.")
    @ErrorApiResponse(responseCode = "400", description = "Password format is invalid or the current password does not match")
    @ErrorApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @Parameter(description = "University identifier in ####-#### format.", example = "2025-4321")
            @PathVariable String universityId,
            @Valid @RequestBody ChangePasswordDTO request) {
        service.updatePassword(universityId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Password updated successfully"));
    }

    /**
     * Deletes the account identified by the path value.
     *
     * @param universityId path identifier of the user to delete
     * @return an empty success envelope when deletion completes
     */
    @DeleteMapping("/{universityId}")
    @Operation(summary = "Delete a user account",
            description = "Permanently deletes the account identified by university ID.")
    @ErrorApiResponse(responseCode = "400", description = "University ID must use the ####-#### format")
    @ErrorApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @Parameter(description = "University identifier in ####-#### format.", example = "2025-4321")
            @PathVariable String universityId) {
        service.deleteUserByUniversityId(universityId);
        return ResponseEntity.ok(new ApiResponse<>(true, "User deleted successfully"));
    }
}
