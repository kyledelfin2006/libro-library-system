package integration.user;

import app.LibraryApplication;
import app.user.dto.ChangePasswordDTO;
import app.user.dto.UserCreateRequestDTO;
import app.user.dto.UserCreateUpdateDTO;
import app.user.dto.UserResponseDTO;
import app.user.entity.User;
import app.user.entity.enums.UserCourse;
import app.user.entity.enums.UserITMajor;
import app.user.entity.enums.UserRole;
import app.user.repository.UserRepository;
import app.user.service.UserService;
import integration.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = LibraryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(PostgresTestConfig.class)
@ResourceLock("postgres")
class UserPersistenceIT {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createAndUpdatePersistNormalizedProfileAndEncodedPassword() {
        try {
            UserResponseDTO created = userService.createUser(new UserCreateRequestDTO(
                    "2025-4321", "CurrentPass1!", "Alex", "Rivera", "M",
                    "Alex.Rivera@Example.edu", UserRole.STUDENT, UserCourse.IT, UserITMajor.SE));

            assertEquals("alex.rivera@example.edu", created.getEmail());
            User stored = userRepository.findByUniversityId("2025-4321").orElseThrow();
            assertFalse(stored.getPasswordHash().equals("CurrentPass1!"));
            assertTrue(passwordEncoder.matches("CurrentPass1!", stored.getPasswordHash()));

            userService.updateUser("2025-4321", new UserCreateUpdateDTO(
                    " Alexandra ", null, null, " New.Email@Example.edu "));
            userService.updatePassword("2025-4321", new ChangePasswordDTO(
                    "CurrentPass1!", "NewPassword2@"));

            User updated = userRepository.findByUniversityId("2025-4321").orElseThrow();
            assertEquals("Alexandra", updated.getFirstName());
            assertEquals("Rivera", updated.getLastName());
            assertEquals("new.email@example.edu", updated.getEmail());
            assertTrue(passwordEncoder.matches("NewPassword2@", updated.getPasswordHash()));
            assertFalse(passwordEncoder.matches("CurrentPass1!", updated.getPasswordHash()));
        } finally {
            userRepository.deleteAllInBatch();
        }
    }

    @Test
    @Transactional
    void postgresRejectsDuplicateEmailAfterServiceNormalizesIt() {
        userService.createUser(new UserCreateRequestDTO(
                "2025-4321", "CurrentPass1!", "Alex", "Rivera", null,
                "Alex.Rivera@Example.edu", UserRole.STUDENT, UserCourse.IT, UserITMajor.SE));

        User duplicate = new User();
        duplicate.setUniversityId("2025-9999");
        duplicate.setPasswordHash("encoded-test-hash");
        duplicate.setFirstName("Other");
        duplicate.setLastName("Student");
        duplicate.setEmail("alex.rivera@example.edu");
        duplicate.setUserRole(UserRole.STUDENT);
        duplicate.setUserCourse(UserCourse.IT);
        duplicate.setMajor(UserITMajor.SE);

        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(duplicate));
    }

    @Test
    void postgresEnforcesUniversityIdFormatConstraint() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO users (university_id, password_hash, last_name, first_name, email,
                                   user_role, student_course, infotech_major)
                VALUES ('invalid-id', 'encoded-test-hash', 'Example', 'Test', 'test@example.edu',
                        'STUDENT', 'IT', 'SE')
                """));
    }
}
