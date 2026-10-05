package unit.auth;

import app.LibraryApplication;
import app.auth.LibroUserDetailsService;
import app.auth.SecurityConfig;
import app.book.controller.BookAPI;
import app.book.dto.BookResponseDTO;
import app.book.entity.Book;
import app.book.mapper.BookMapper;
import app.book.service.BookService;
import app.global.exceptions.GlobalExceptionHandler;
import app.user.config.PasswordConfig;
import app.user.controller.UserAPI;
import app.user.dto.UserResponseDTO;
import app.user.entity.User;
import app.user.entity.enums.UserCourse;
import app.user.entity.enums.UserITMajor;
import app.user.entity.enums.UserRole;
import app.user.repository.UserRepository;
import app.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MVC tests for the real Libro security chain and account lookup. */
@WebMvcTest({BookAPI.class, UserAPI.class})
@AutoConfigureMockMvc
@Import({
        SecurityConfig.class,
        LibroUserDetailsService.class,
        PasswordConfig.class,
        GlobalExceptionHandler.class,
        SecurityFlowMvcTest.MockBeans.class
})
@ContextConfiguration(classes = LibraryApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityFlowMvcTest {

    private static final String UNIVERSITY_ID = "2025-4321";
    private static final String PASSWORD = "GoodPassword1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private BookService bookService;

    @Autowired
    private BookMapper bookMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String passwordHash;

    @TestConfiguration(proxyBeanMethods = false)
    static class MockBeans {
        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        UserService userService() {
            return mock(UserService.class);
        }

        @Bean
        BookService bookService() {
            return mock(BookService.class);
        }

        @Bean
        BookMapper bookMapper() {
            return mock(BookMapper.class);
        }
    }

    @org.junit.jupiter.api.BeforeAll
    void encodePasswordOnce() {
        passwordHash = passwordEncoder.encode(PASSWORD);
    }

    @BeforeEach
    void resetMocks() {
        reset(userRepository, userService, bookService, bookMapper);
    }

    @Test
    void signupIsAccessibleWithoutBasicCredentials() throws Exception {
        when(userService.createUser(any())).thenReturn(userResponse());

        mockMvc.perform(post("/app/users/signup")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "universityId":"2025-4321",
                                  "password":"GoodPassword1!",
                                  "firstName":"Alex",
                                  "lastName":"Rivera",
                                  "email":"alex.rivera@example.edu",
                                  "userRole":"STUDENT",
                                  "userCourse":"IT",
                                  "major":"SE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.universityId").value(UNIVERSITY_ID));

        verify(userService).createUser(any());
    }

    @Test
    void protectedBookEndpointRejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/app/books/1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bookService, bookMapper);
    }

    @Test
    void validBasicCredentialsLoadLibroUserAndReachBookController() throws Exception {
        User user = libroUser();
        Book book = book();
        when(userRepository.findByUniversityId(UNIVERSITY_ID)).thenReturn(Optional.of(user));
        when(bookService.findBookById(1L)).thenReturn(book);
        when(bookMapper.toResponseDTO(book)).thenReturn(bookResponse());

        mockMvc.perform(get("/app/books/1")
                        .header(HttpHeaders.AUTHORIZATION, basicAuthorization(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(userRepository).findByUniversityId(UNIVERSITY_ID);
        verify(bookService).findBookById(1L);
        verify(bookMapper).toResponseDTO(book);
    }

    @Test
    void incorrectBasicPasswordDoesNotReachBookController() throws Exception {
        when(userRepository.findByUniversityId(UNIVERSITY_ID)).thenReturn(Optional.of(libroUser()));

        mockMvc.perform(get("/app/books/1")
                        .header(HttpHeaders.AUTHORIZATION, basicAuthorization("WrongPassword1!")))
                .andExpect(status().isUnauthorized());

        verify(userRepository).findByUniversityId(UNIVERSITY_ID);
        verify(bookService, never()).findBookById(any());
        verifyNoInteractions(bookMapper);
    }

    private User libroUser() {
        User user = new User();
        user.setUniversityId(UNIVERSITY_ID);
        user.setPasswordHash(passwordHash);
        user.setUserRole(UserRole.STUDENT);
        return user;
    }

    private UserResponseDTO userResponse() {
        return new UserResponseDTO(UNIVERSITY_ID, "Alex", "Rivera", null,
                "alex.rivera@example.edu", UserRole.STUDENT, UserCourse.IT, UserITMajor.SE);
    }

    private Book book() {
        Book book = new Book("1984", "George Orwell", "Dystopian", new BigDecimal("19.99"));
        book.setId(1L);
        return book;
    }

    private BookResponseDTO bookResponse() {
        return new BookResponseDTO(1L, "1984", "George Orwell", "Dystopian", new BigDecimal("19.99"));
    }

    private String basicAuthorization(String password) {
        String credentials = UNIVERSITY_ID + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
