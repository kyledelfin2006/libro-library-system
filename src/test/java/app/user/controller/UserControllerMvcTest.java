package app.user.controller;

import app.LibraryApplication;
import app.global.exception.GlobalExceptionHandler;
import app.user.dto.PasswordChangeRequestDTO;
import app.user.dto.PasswordResetRequestDTO;
import app.user.dto.UserCreateRequestDTO;
import app.user.dto.UserPatchRequestDTO;
import app.user.dto.UserReplaceRequestDTO;
import app.user.dto.UserResponseDTO;
import app.user.entity.enums.UserCourse;
import app.user.entity.enums.UserITMajor;
import app.user.entity.enums.UserRole;
import app.user.exception.UserNotFoundException;
import app.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MVC contract tests for user routes, with business behavior isolated behind a mocked service. */
@ActiveProfiles("test")
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, UserControllerMvcTest.MockBeans.class})
@ContextConfiguration(classes = LibraryApplication.class)
class UserControllerMvcTest {

    private static final String USER_ID = "2025-4321";
    // Filters are disabled in this controller slice, so attach the principal to
    // the mock request explicitly for Authentication method parameters.
    private static final Authentication CALLER = new UsernamePasswordAuthenticationToken(
            USER_ID, "", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService service;

    @TestConfiguration(proxyBeanMethods = false)
    static class MockBeans {

        @Bean
        UserService userService() {
            return mock(UserService.class);
        }
    }

    @BeforeEach
    void resetService() {
        reset(service);
    }

    @Test
    void createUser_returnsCreatedEnvelopeAndPublicProfile() throws Exception {
        when(service.createUser(any(UserCreateRequestDTO.class))).thenReturn(user());

        mockMvc.perform(post("/app/users/signup")
                        .contentType(APPLICATION_JSON)
                        .content(createRequest()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User created successfully"))
                .andExpect(jsonPath("$.data.universityId").value(USER_ID))
                .andExpect(jsonPath("$.data.email").value("alex.rivera@example.edu"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        ArgumentCaptor<UserCreateRequestDTO> request = ArgumentCaptor.forClass(UserCreateRequestDTO.class);
        verify(service).createUser(request.capture());
        assertEquals(USER_ID, request.getValue().getUniversityId());
        assertEquals(UserRole.STUDENT, request.getValue().getUserRole());
        assertEquals(UserCourse.IT, request.getValue().getUserCourse());
        assertEquals(UserITMajor.SE, request.getValue().getMajor());
    }

    @Test
    void createUser_withInvalidPayloadReturnsFieldErrorsWithoutCallingService() throws Exception {
        mockMvc.perform(post("/app/users/signup")
                        .contentType(APPLICATION_JSON)
                        .content("{\"universityId\":\"bad\",\"password\":\"weak\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.fieldErrors.universityId").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists());

        verify(service, never()).createUser(any());
    }

    @Test
    void createUser_whenDatabaseUniqueConstraintConflictsReturnsConflictContract() throws Exception {
        when(service.createUser(any(UserCreateRequestDTO.class)))
                .thenThrow(new DataIntegrityViolationException("internal constraint detail"));

        mockMvc.perform(post("/app/users/signup")
                        .contentType(APPLICATION_JSON)
                        .content(createRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Data integrity violation"))
                .andExpect(jsonPath("$.details").value("The operation would violate a database constraint"))
                .andExpect(jsonPath("$.details").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal constraint detail"))))
                .andExpect(jsonPath("$.statusCode").value(409));
    }

    @Test
    void createAdministrator_returnsCreatedLibraryAssistantProfile() throws Exception {
        UserResponseDTO assistant = new UserResponseDTO("2025-8888", "Library", "Assistant", null,
                "assistant@example.edu", UserRole.ADMINISTRATOR, null, null, false);
        when(service.createAdministrator(any(UserCreateRequestDTO.class), anyCollection())).thenReturn(assistant);

        mockMvc.perform(post("/app/users/administrators")
                        .principal(CALLER)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"universityId":"2025-8888","password":"ValidPass1!",
                                 "firstName":"Library","lastName":"Assistant",
                                 "email":"assistant@example.edu","userRole":"LIBRARIAN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userRole").value("ADMINISTRATOR"));

        verify(service).createAdministrator(any(UserCreateRequestDTO.class), anyCollection());
    }

    @Test
    void listUsers_returnsPageAndUsesDocumentedDefaultPagination() throws Exception {
        when(service.getAllUsers(any(Pageable.class), anyString(), anyCollection())).thenReturn(
                new PageImpl<>(List.of(user()), PageRequest.of(0, 12), 1));

        mockMvc.perform(get("/app/users").principal(CALLER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].universityId").value(USER_ID))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(12))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service).getAllUsers(pageable.capture(), eq(USER_ID), anyCollection());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(12, pageable.getValue().getPageSize());
        assertEquals("universityId", pageable.getValue().getSort().iterator().next().getProperty());
    }

    @Test
    void listUsers_bindsPageSizeAndSort() throws Exception {
        when(service.getAllUsers(any(Pageable.class), anyString(), anyCollection())).thenReturn(
                new PageImpl<>(List.of(user()), PageRequest.of(1, 2), 3));

        mockMvc.perform(get("/app/users")
                        .principal(CALLER)
                        .queryParam("page", "1")
                        .queryParam("size", "2")
                        .queryParam("sort", "lastName,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service).getAllUsers(pageable.capture(), eq(USER_ID), anyCollection());
        assertEquals("lastName", pageable.getValue().getSort().iterator().next().getProperty());
        assertEquals(org.springframework.data.domain.Sort.Direction.DESC,
                pageable.getValue().getSort().iterator().next().getDirection());
    }

    @Test
    void getUser_returnsDirectPublicProfileDto() throws Exception {
        when(service.getUserByUniversityId(eq(USER_ID), eq(USER_ID), anyCollection())).thenReturn(user());

        mockMvc.perform(get("/app/users/{universityId}", USER_ID).principal(CALLER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.universityId").value(USER_ID))
                .andExpect(jsonPath("$.data.firstName").value("Alex"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void getUser_whenMissingReturnsNotFoundContract() throws Exception {
        when(service.getUserByUniversityId(eq(USER_ID), eq(USER_ID), anyCollection()))
                .thenThrow(new UserNotFoundException("Couldn't find user of ID: " + USER_ID));

        mockMvc.perform(get("/app/users/{universityId}", USER_ID).principal(CALLER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found"))
                .andExpect(jsonPath("$.details").value("Couldn't find user of ID: " + USER_ID))
                .andExpect(jsonPath("$.statusCode").value(404));
    }

    /** The own-profile route uses only the verified principal and never serializes credentials. */
    @Test
    void getMyProfile_usesPrincipalAndReturnsPublicDto() throws Exception {
        when(service.getUserByUniversityId(eq(USER_ID), eq(USER_ID), anyCollection())).thenReturn(user());

        mockMvc.perform(get("/app/users/me").principal(CALLER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.universityId").value(USER_ID))
                .andExpect(jsonPath("$.data.email").value("alex.rivera@example.edu"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        verify(service).getUserByUniversityId(eq(USER_ID), eq(USER_ID), anyCollection());
    }

    /** A still-valid token for an account deleted since issue receives 404. */
    @Test
    void getMyProfile_whenAccountWasDeletedReturnsNotFound() throws Exception {
        when(service.getUserByUniversityId(eq(USER_ID), eq(USER_ID), anyCollection()))
                .thenThrow(new UserNotFoundException(USER_ID));

        mockMvc.perform(get("/app/users/me").principal(CALLER))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchUser_returnsSuccessEnvelopeAndForwardsPartialBody() throws Exception {
        when(service.updateUser(eq(USER_ID), eq(USER_ID), any(UserPatchRequestDTO.class), anyCollection())).thenReturn(user());

        mockMvc.perform(patch("/app/users/{universityId}", USER_ID)
                        .principal(CALLER)
                        .contentType(APPLICATION_JSON)
                        .content("{\"firstName\":\"Alexandra\",\"email\":\"alexandra@example.edu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User updated successfully"))
                .andExpect(jsonPath("$.data.universityId").value(USER_ID));

        ArgumentCaptor<UserPatchRequestDTO> request = ArgumentCaptor.forClass(UserPatchRequestDTO.class);
        verify(service).updateUser(eq(USER_ID), eq(USER_ID), request.capture(), anyCollection());
        assertEquals("Alexandra", request.getValue().getFirstName());
        assertEquals("alexandra@example.edu", request.getValue().getEmail());
    }

    @Test
    void replaceUser_returnsSuccessEnvelopeAndRequiresCompleteProfileFields() throws Exception {
        when(service.replaceUserProfile(eq(USER_ID), eq(USER_ID), any(UserReplaceRequestDTO.class), anyCollection())).thenReturn(user());

        mockMvc.perform(put("/app/users/{universityId}", USER_ID)
                        .principal(CALLER)
                        .contentType(APPLICATION_JSON)
                        .content("{\"firstName\":\"Alex\",\"lastName\":\"Rivera\",\"middleInitial\":\"M\",\"email\":\"alex@example.edu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User profile replaced successfully"))
                .andExpect(jsonPath("$.data.universityId").value(USER_ID));

        verify(service).replaceUserProfile(eq(USER_ID), eq(USER_ID), any(UserReplaceRequestDTO.class), anyCollection());
    }

    @Test
    void replaceUser_withMissingRequiredFieldsReturnsValidationError() throws Exception {
        mockMvc.perform(put("/app/users/{universityId}", USER_ID)
                        .contentType(APPLICATION_JSON)
                        .content("{\"middleInitial\":\"M\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                .andExpect(jsonPath("$.fieldErrors.lastName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        verify(service, never()).replaceUserProfile(any(), any(), any(), anyCollection());
    }

    @Test
    void changePassword_returnsEmptySuccessEnvelopeAndForwardsRequest() throws Exception {
        mockMvc.perform(put("/app/users/{universityId}/password", USER_ID)
                        .principal(CALLER)
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"OldPass1!\",\"newPassword\":\"NewPass2!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password updated successfully"))
                .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<PasswordChangeRequestDTO> request = ArgumentCaptor.forClass(PasswordChangeRequestDTO.class);
        verify(service).updatePassword(eq(USER_ID), eq(USER_ID), request.capture(), anyCollection());
        assertEquals("OldPass1!", request.getValue().getCurrentPassword());
        assertEquals("NewPass2!", request.getValue().getNewPassword());
    }

    @Test
    void changePassword_withInvalidPasswordReturnsFieldErrorsWithoutCallingService() throws Exception {
        mockMvc.perform(put("/app/users/{universityId}/password", USER_ID)
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"weak\",\"newPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());

        verify(service, never()).updatePassword(any(), any(), any(), anyCollection());
    }

    /** Staff reset accepts only a valid temporary password and returns no secret. */
    @Test
    void resetPassword_returnsEmptyEnvelopeAndForwardsTemporaryPassword() throws Exception {
        mockMvc.perform(put("/app/users/{universityId}/password/reset", USER_ID)
                        .principal(CALLER).contentType(APPLICATION_JSON)
                        .content("{\"temporaryPassword\":\"Temporary1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist());

        ArgumentCaptor<PasswordResetRequestDTO> request = ArgumentCaptor.forClass(PasswordResetRequestDTO.class);
        verify(service).resetPassword(eq(USER_ID), eq(USER_ID), request.capture(), anyCollection());
        assertEquals("Temporary1!", request.getValue().temporaryPassword());
    }

    /** Invalid recovery input is rejected before the service can hash it. */
    @Test
    void resetPassword_rejectsWeakTemporaryPassword() throws Exception {
        mockMvc.perform(put("/app/users/{universityId}/password/reset", USER_ID)
                        .principal(CALLER).contentType(APPLICATION_JSON)
                        .content("{\"temporaryPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.temporaryPassword").exists());
        verify(service, never()).resetPassword(any(), any(), any(), anyCollection());
    }

    @Test
    void deleteUser_returnsSuccessEnvelope() throws Exception {
        mockMvc.perform(delete("/app/users/{universityId}", USER_ID).principal(CALLER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User deleted successfully"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(service).deleteUserByUniversityId(eq(USER_ID), eq(USER_ID), anyCollection());
    }

    private String createRequest() {
        return """
                {
                  "universityId":"2025-4321",
                  "password":"ValidPass1!",
                  "firstName":"Alex",
                  "lastName":"Rivera",
                  "middleInitial":"M",
                  "email":"alex.rivera@example.edu",
                  "userRole":"STUDENT",
                  "userCourse":"IT",
                  "major":"SE"
                }
                """;
    }

    private UserResponseDTO user() {
        return new UserResponseDTO(USER_ID, "Alex", "Rivera", "M",
                "alex.rivera@example.edu", UserRole.STUDENT, UserCourse.IT, UserITMajor.SE, false);
    }
}
