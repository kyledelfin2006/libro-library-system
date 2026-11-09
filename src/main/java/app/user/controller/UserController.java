package app.user.controller;

import app.global.response.ApiResponse;
import app.global.openapi.ErrorApiResponse;
import app.user.dto.PasswordChangeRequestDTO;
import app.user.dto.PasswordResetRequestDTO;
import app.user.dto.UserCreateRequestDTO;
import app.user.dto.UserPatchRequestDTO;
import app.user.dto.UserReplaceRequestDTO;
import app.user.dto.UserResponseDTO;
import app.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
import org.springframework.web.bind.annotation.RequestParam;
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
public class UserController {

    private final UserService service;

    /**
     * Creates the controller with its user-domain service.
     *
     * @param service user account operations
     */
    public UserController(UserService service) {
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
    @ErrorApiResponse(responseCode = "429", description = "This client has sent too many signup requests")
    public ResponseEntity<ApiResponse<UserResponseDTO>> createUser(
            @Valid @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
                    value = "{\"universityId\":\"2025-4321\",\"password\":\"ExampleOnly1!\",\"firstName\":\"Alex\",\"lastName\":\"Rivera\",\"email\":\"alex.rivera@example.edu\",\"userCourse\":\"IS\"}")))
            UserCreateRequestDTO request) {
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
            description = "Library assistants may list student and school faculty profiles. The librarian may also list library assistant profiles. Optional role and q filters stay inside that scope; q matches a case-insensitive literal substring of ID, name, or email. An out-of-scope role returns 403. Page numbering starts at zero. The default is page 0, size 12, sorted by universityId ascending; the maximum page size is 100. Sort with sort=property,direction using universityId, firstName, lastName, middleInitial, email, userRole, userCourse, or major. Unsupported sort properties return 400.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authorized account page", useReturnTypeSchema = true)
    @ErrorApiResponse(responseCode = "400", description = "Invalid pagination or sort parameter")
    @ErrorApiResponse(responseCode = "403", description = "Only library assistants and the librarian may list accounts")
    public ResponseEntity<ApiResponse<Page<UserResponseDTO>>> getAllUsers(
            @io.swagger.v3.oas.annotations.Parameter(description = "Optional target role within the caller's staff scope; LIBRARIAN is never listed.")
            @RequestParam(required = false) app.user.entity.enums.UserRole role,
            @io.swagger.v3.oas.annotations.Parameter(description = "Case-insensitive literal substring of university ID, first name, last name, or email.")
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 12, sort = "universityId") Pageable pageable,
            Authentication authentication) {
        // Forward the verified caller identity and roles for the service authorization check.
        Page<UserResponseDTO> users = role == null && (q == null || q.isBlank())
                ? service.getAllUsers(pageable, authentication.getName(), authentication.getAuthorities())
                : service.getAllUsers(pageable, authentication.getName(), authentication.getAuthorities(), role, q);
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    /** Returns the authenticated account's current public profile. */
    @GetMapping("/me")
    @Operation(summary = "Get my profile",
            description = "All four roles may view their own current profile. The account is loaded from the Bearer token subject; deleted accounts return 404. Password data is never returned.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Current public profile", useReturnTypeSchema = true)
    @ErrorApiResponse(responseCode = "401", description = "Bearer token is missing or invalid")
    @ErrorApiResponse(responseCode = "404", description = "Authenticated account no longer exists")
    public ResponseEntity<ApiResponse<UserResponseDTO>> getMyProfile(Authentication authentication) {
        // Use the verified subject for both caller and target, so no client-supplied ID selects an account.
        return ResponseEntity.ok(ApiResponse.ok(service.getUserByUniversityId(
                authentication.getName(), authentication.getName(), authentication.getAuthorities())));
    }

    /**
     * Retrieves one public profile in the standard success envelope.
     *
     * @param universityId path identifier of the user
     * @return the matching public profile
     */
    @GetMapping("/{universityId}")
    @Operation(summary = "Get a user by university ID",
            description = "Users may view their own profile. Library assistants and the librarian may also view student and school faculty profiles; only the librarian may view library assistant profiles. Passwords and password hashes are never included.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Public account profile", useReturnTypeSchema = true)
    @ErrorApiResponse(responseCode = "400", description = "University ID must use the ####-#### format")
    @ErrorApiResponse(responseCode = "404", description = "User not found")
    @ErrorApiResponse(responseCode = "403", description = "Only the account owner or authorized staff may view this profile")
    public ResponseEntity<ApiResponse<UserResponseDTO>> getUserByUniversityId(
            @Parameter(description = "University identifier in ####-#### format.", example = "2025-4321")
            @PathVariable String universityId,
            Authentication authentication) {
        // Pass the requested account and verified caller context to the service.
        return ResponseEntity.ok(ApiResponse.ok(service.getUserByUniversityId(authentication.getName(), universityId,
                authentication.getAuthorities())));
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
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated public profile", useReturnTypeSchema = true)
    @ErrorApiResponse(responseCode = "400", description = "Invalid profile field, university ID, or email is already registered")
    @ErrorApiResponse(responseCode = "404", description = "User not found")
    @ErrorApiResponse(responseCode = "409", description = "A database uniqueness constraint was concurrently violated")
    @ErrorApiResponse(responseCode = "403", description = "Only the account owner or authorized staff may update this profile")
    public ResponseEntity<ApiResponse<UserResponseDTO>> updateUser(
            @Parameter(description = "University identifier in ####-#### format.", example = "2025-4321")
            @PathVariable String universityId,
            @RequestBody UserPatchRequestDTO request,
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
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Replaced public profile", useReturnTypeSchema = true)
    @ErrorApiResponse(responseCode = "400", description = "Request validation failed, university ID is invalid, or email is already registered")
    @ErrorApiResponse(responseCode = "404", description = "User not found")
    @ErrorApiResponse(responseCode = "409", description = "A database uniqueness constraint was concurrently violated")
    @ErrorApiResponse(responseCode = "403", description = "Only the account owner or authorized staff may replace this profile")
    public ResponseEntity<ApiResponse<UserResponseDTO>> replaceUserProfile(
            @Parameter(description = "University identifier in ####-#### format.", example = "2025-4321")
            @PathVariable String universityId,
            @Valid @RequestBody UserReplaceRequestDTO request,
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
            description = "Account owners provide their current password and a new password. A successful change clears mustChangePassword. Staff-assisted recovery uses the separate reset route. The new password must meet the 8–72 character complexity policy; password values are never returned.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed", useReturnTypeSchema = true)
    @ErrorApiResponse(responseCode = "400", description = "Password format is invalid or the current password does not match")
    @ErrorApiResponse(responseCode = "404", description = "User not found")
    @ErrorApiResponse(responseCode = "403", description = "Only the account owner or authorized staff may change this password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @Parameter(description = "University identifier in ####-#### format.", example = "2025-4321")
            @PathVariable String universityId,
            @Valid @RequestBody PasswordChangeRequestDTO request,
            Authentication authentication) {
        // The service checks owner/staff access and verifies current password for owner changes.
        service.updatePassword(authentication.getName(), universityId, request,
                authentication.getAuthorities());
        return ResponseEntity.ok(new ApiResponse<>(true, "Password updated successfully"));
    }

    /** Lets authorized staff set a temporary password and require the owner to change it. */
    @PutMapping("/{universityId}/password/reset")
    @Operation(summary = "Reset a managed account password",
            description = "Library assistants may reset student and faculty accounts; librarians may also reset library assistants. Staff cannot reset their own or a librarian account. The temporary password is never returned, and the account receives mustChangePassword=true until its owner changes it.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Temporary password set", useReturnTypeSchema = true)
    @ErrorApiResponse(responseCode = "400", description = "Temporary password or university ID is invalid")
    @ErrorApiResponse(responseCode = "403", description = "Caller cannot reset this account")
    @ErrorApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Parameter(description = "University identifier in ####-#### format.", example = "2025-4321")
            @PathVariable String universityId,
            @Valid @RequestBody PasswordResetRequestDTO request,
            Authentication authentication) {
        // The service checks the target role and denies self-reset before changing the hash.
        service.resetPassword(authentication.getName(), universityId, request,
                authentication.getAuthorities());
        return ResponseEntity.ok(new ApiResponse<>(true, "Temporary password set"));
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
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account deleted", useReturnTypeSchema = true)
    @ErrorApiResponse(responseCode = "400", description = "University ID must use the ####-#### format")
    @ErrorApiResponse(responseCode = "404", description = "User not found")
    @ErrorApiResponse(responseCode = "403", description = "The caller cannot delete the requested account")
    @ErrorApiResponse(responseCode = "409", description = "Account has loan history and must be retained")
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
