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
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
@Tag(name = "Users", description = "Students and school faculty use patron accounts. Administrators are library assistants; librarians manage faculty and library assistant accounts.")
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
    @PostMapping("/signup")
    @SecurityRequirements
    @Operation(summary = "Sign up for a user account",
            description = "Creates a student account; any supplied role is ignored. Students must provide a course, and IT students must provide an approved major. Passwords must be 8–72 characters and include uppercase and lowercase letters, a number, and a symbol. Names are trimmed and email is stored lowercase.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User created")
    @ErrorApiResponse(responseCode = "400", description = "Request validation failed or university ID/email is already registered")
    @ErrorApiResponse(responseCode = "409", description = "A database uniqueness constraint was concurrently violated")
    public ResponseEntity<ApiResponse<UserResponseDTO>> createUser(
            @Valid @RequestBody UserCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "User created successfully", service.createUser(request)));
    }

    @PostMapping("/faculty")
    @Operation(summary = "Create a faculty account", description = "Creates a faculty account. Only librarians may use this operation; the submitted role is ignored and the account is always FACULTY.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Faculty account created")
    @ErrorApiResponse(responseCode = "400", description = "Request validation failed or university ID/email is already registered")
    @ErrorApiResponse(responseCode = "403", description = "Only librarians may create faculty accounts")
    public ResponseEntity<ApiResponse<UserResponseDTO>> createFaculty(
            @Valid @RequestBody UserCreateRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Faculty account created successfully",
                        service.createFaculty(request, authentication.getAuthorities())));
    }

    /** Creates a library assistant account. */
    @PostMapping("/administrators")
    @Operation(summary = "Create a library assistant account",
            description = "Only the library librarian may create a library assistant account. The submitted role is ignored and the account is always ADMINISTRATOR.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Library assistant account created")
    @ErrorApiResponse(responseCode = "400", description = "Request validation failed or university ID/email is already registered")
    @ErrorApiResponse(responseCode = "403", description = "Only the librarian may create library assistant accounts")
    public ResponseEntity<ApiResponse<UserResponseDTO>> createAdministrator(
            @Valid @RequestBody UserCreateRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Library assistant account created successfully",
                        service.createAdministrator(request, authentication.getAuthorities())));
    }

    /**
     * Lists public user profiles using Spring's bound pagination parameters.
     *
     * @param pageable requested page, size, and sort order
     * @return the requested page of public profiles
     */
    @GetMapping
    @Operation(summary = "List accounts with pagination",
            description = "Library assistants may list student and school faculty profiles. The librarian may also list library assistant profiles. Page numbering starts at zero. The default is page 0, size 12, sorted by universityId ascending; the maximum page size is 100. Sort with sort=property,direction using universityId, firstName, lastName, middleInitial, email, userRole, userCourse, or major. Unsupported sort properties return 400.")
    @ErrorApiResponse(responseCode = "400", description = "Invalid pagination or sort parameter")
    @ErrorApiResponse(responseCode = "403", description = "Only library assistants and the librarian may list accounts")
    public ResponseEntity<Page<UserResponseDTO>> getAllUsers(
            @ParameterObject @PageableDefault(size = 12, sort = "universityId") Pageable pageable,
            Authentication authentication) {
        // Forward the verified caller identity and roles for the service authorization check.
        return ResponseEntity.ok(service.getAllUsers(pageable, authentication.getName(),
                authentication.getAuthorities()));
    }

    /**
     * Retrieves one public profile without wrapping it in {@link ApiResponse}.
     *
     * @param universityId path identifier of the user
     * @return the matching public profile
     */
    @GetMapping("/{universityId}")
    @Operation(summary = "Get a user by university ID",
            description = "Users may view their own profile. Library assistants and the librarian may also view student and school faculty profiles; only the librarian may view library assistant profiles. Passwords and password hashes are never included.")
    @ErrorApiResponse(responseCode = "400", description = "University ID must use the ####-#### format")
    @ErrorApiResponse(responseCode = "404", description = "User not found")
    @ErrorApiResponse(responseCode = "403", description = "Only the account owner or authorized staff may view this profile")
    public ResponseEntity<UserResponseDTO> getUserByUniversityId(
            @Parameter(description = "University identifier in ####-#### format.", example = "2025-4321")
            @PathVariable String universityId,
            Authentication authentication) {
        // Pass the requested account and verified caller context to the service.
        return ResponseEntity.ok(service.getUserByUniversityId(authentication.getName(), universityId,
                authentication.getAuthorities()));
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
            description = "Users may update their own profile; library assistants and the librarian may also update student or school faculty profiles, while only the librarian may update library assistant profiles. Updates only supplied fields; null or omitted fields are unchanged. Supplied names and email cannot be blank. A blank middle initial is treated as omitted. Values are trimmed and email is stored lowercase.")
    @ErrorApiResponse(responseCode = "400", description = "Invalid profile field, university ID, or email is already registered")
    @ErrorApiResponse(responseCode = "404", description = "User not found")
    @ErrorApiResponse(responseCode = "409", description = "A database uniqueness constraint was concurrently violated")
    @ErrorApiResponse(responseCode = "403", description = "Only the account owner or authorized staff may update this profile")
    public ResponseEntity<ApiResponse<UserResponseDTO>> updateUser(
            @Parameter(description = "University identifier in ####-#### format.", example = "2025-4321")
            @PathVariable String universityId,
            @RequestBody UserCreateUpdateDTO request,
            Authentication authentication) {
        // Forward the caller context so the service can enforce account ownership.
        return ResponseEntity.ok(new ApiResponse<>(true, "User updated successfully",
                service.updateUser(authentication.getName(), universityId, request,
                        authentication.getAuthorities())));
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
            description = "Users may replace their own profile; library assistants and the librarian may also replace student and school faculty profiles, while only the librarian may replace library assistant profiles. Replaces first name, last name, middle initial, and email. First name, last name, and email are required; university ID, role, course, major, and password are not changed. Names are trimmed and email is stored lowercase.")
    @ErrorApiResponse(responseCode = "400", description = "Request validation failed, university ID is invalid, or email is already registered")
    @ErrorApiResponse(responseCode = "404", description = "User not found")
    @ErrorApiResponse(responseCode = "409", description = "A database uniqueness constraint was concurrently violated")
    @ErrorApiResponse(responseCode = "403", description = "Only the account owner or authorized staff may replace this profile")
    public ResponseEntity<ApiResponse<UserResponseDTO>> replaceUserProfile(
            @Parameter(description = "University identifier in ####-#### format.", example = "2025-4321")
            @PathVariable String universityId,
            @Valid @RequestBody UserReplaceRequest request,
            Authentication authentication) {
        // Forward the caller context so the service can enforce account ownership.
        return ResponseEntity.ok(new ApiResponse<>(true, "User profile replaced successfully",
                service.replaceUserProfile(authentication.getName(), universityId, request,
                        authentication.getAuthorities())));
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
            description = "Users must provide their current password to change their own password. Library assistants and the librarian may reset student and school faculty passwords without the target user's current password; only the librarian may reset a library assistant's password. The new password must meet the 8–72 character complexity policy; password values are never returned.")
    @ErrorApiResponse(responseCode = "400", description = "Password format is invalid or the current password does not match")
    @ErrorApiResponse(responseCode = "404", description = "User not found")
    @ErrorApiResponse(responseCode = "403", description = "Only the account owner or authorized staff may change this password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @Parameter(description = "University identifier in ####-#### format.", example = "2025-4321")
            @PathVariable String universityId,
            @Valid @RequestBody ChangePasswordDTO request,
            Authentication authentication) {
        // The service checks owner/staff access and verifies current password for owner changes.
        service.updatePassword(authentication.getName(), universityId, request,
                authentication.getAuthorities());
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
            description = "Students and school faculty may delete their own accounts. Library assistants may delete student and faculty accounts; the librarian may also delete library assistant accounts. Librarian accounts are protected.")
    @ErrorApiResponse(responseCode = "400", description = "University ID must use the ####-#### format")
    @ErrorApiResponse(responseCode = "404", description = "User not found")
    @ErrorApiResponse(responseCode = "403", description = "The caller cannot delete the requested account")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @Parameter(description = "University identifier in ####-#### format.", example = "2025-4321")
            @PathVariable String universityId,
            Authentication authentication) {
        // Pass verified roles so the service enforces the deletion boundary too.
        service.deleteUserByUniversityId(authentication.getName(), universityId,
                authentication.getAuthorities());
        return ResponseEntity.ok(new ApiResponse<>(true, "User deleted successfully"));
    }
}
