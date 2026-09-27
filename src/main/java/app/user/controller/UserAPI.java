package app.user.controller;

import app.global.responses.ApiResponse;
import app.user.dto.ChangePasswordDTO;
import app.user.dto.UserCreateRequestDTO;
import app.user.dto.UserCreateUpdateDTO;
import app.user.dto.UserReplaceRequest;
import app.user.dto.UserResponseDTO;
import app.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
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

@RestController
@RequestMapping("/app/users")
@Tag(name = "Users", description = "User account and profile operations")
public class UserAPI {

    private final UserService service;

    public UserAPI(UserService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a user account")
    public ResponseEntity<ApiResponse<UserResponseDTO>> createUser(
            @Valid @RequestBody UserCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "User created successfully", service.createUser(request)));
    }

    @GetMapping
    @Operation(summary = "List users with pagination")
    public ResponseEntity<Page<UserResponseDTO>> getAllUsers(
            @ParameterObject @PageableDefault(size = 12, sort = "universityId") Pageable pageable) {
        return ResponseEntity.ok(service.getAllUsers(pageable));
    }

    @GetMapping("/{universityId}")
    @Operation(summary = "Get a user by university ID")
    public ResponseEntity<UserResponseDTO> getUserByUniversityId(
            @PathVariable String universityId) {
        return ResponseEntity.ok(service.getUserByUniversityId(universityId));
    }

    @PatchMapping("/{universityId}")
    @Operation(summary = "Update supplied profile fields")
    public ResponseEntity<ApiResponse<UserResponseDTO>> updateUser(
            @PathVariable String universityId,
            @RequestBody UserCreateUpdateDTO request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "User updated successfully",
                service.updateUser(universityId, request)));
    }

    @PutMapping("/{universityId}")
    @Operation(summary = "Replace a user's profile")
    public ResponseEntity<ApiResponse<UserResponseDTO>> replaceUserProfile(
            @PathVariable String universityId,
            @Valid @RequestBody UserReplaceRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "User profile replaced successfully",
                service.replaceUserProfile(universityId, request)));
    }

    @PutMapping("/{universityId}/password")
    @Operation(summary = "Change a user's password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @PathVariable String universityId,
            @Valid @RequestBody ChangePasswordDTO request) {
        service.updatePassword(universityId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Password updated successfully"));
    }

    @DeleteMapping("/{universityId}")
    @Operation(summary = "Delete a user account")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable String universityId) {
        service.deleteUserByUniversityId(universityId);
        return ResponseEntity.ok(new ApiResponse<>(true, "User deleted successfully"));
    }
}
