package integration.user;

import app.LibraryApplication;
import app.user.config.LibrarianBootstrapProperties;
import app.user.config.LibrarianBootstrapRunner;
import app.user.entity.enums.UserRole;
import app.user.repository.UserRepository;
import integration.PostgresTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Proves the opt-in librarian bootstrap writes an idempotent account to PostgreSQL. */
@SpringBootTest(classes = LibraryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(PostgresTestConfig.class)
@ResourceLock("postgres")
class LibrarianBootstrapIT {

    private static final String UNIVERSITY_ID = "2026-9998";
    private static final String EMAIL = "bootstrap.test.librarian@example.edu";
    private static final String BCRYPT_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;

    @Autowired
    LibrarianBootstrapIT(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Removes only this test's account so its outcomes do not depend on earlier integration methods. */
    @BeforeEach
    void removeExistingFixture() {
        userRepository.findByUniversityId(UNIVERSITY_ID).ifPresent(userRepository::delete);
    }

    /** Deletes the synthetic librarian fixture so other PostgreSQL tests see the unprovisioned baseline. */
    @AfterEach
    void removeCreatedFixture() {
        userRepository.findByUniversityId(UNIVERSITY_ID).ifPresent(userRepository::delete);
    }

    /** Confirms a configured librarian is stored once and repeated startup leaves exactly one librarian. */
    @Test
    void createsTheFirstLibrarianOnceAcrossRepeatedRuns() throws Exception {
        LibrarianBootstrapRunner runner = runner(configuredProperties());

        runner.run(new DefaultApplicationArguments(new String[0]));
        runner.run(new DefaultApplicationArguments(new String[0]));

        var saved = userRepository.findByUniversityId(UNIVERSITY_ID).orElseThrow();
        assertEquals(UserRole.LIBRARIAN, saved.getUserRole());
        assertEquals(EMAIL, saved.getEmail());
        assertEquals(BCRYPT_HASH, saved.getPasswordHash());
        assertEquals(1, userRepository.findByUserRole(UserRole.LIBRARIAN).size());
    }

    /** Confirms an unset all-blank configuration performs no account write. */
    @Test
    void skipsProvisioningWhenAllPropertiesAreUnset() throws Exception {
        LibrarianBootstrapRunner runner = runner(new LibrarianBootstrapProperties(
                null, null, null, null, null));

        runner.run(new DefaultApplicationArguments(new String[0]));

        assertFalse(userRepository.existsByUserRole(UserRole.LIBRARIAN));
    }

    /** Confirms partial configuration and non-BCrypt input fail before the database is changed. */
    @Test
    void rejectsPartialConfigurationAndNonBcryptPasswordInput() {
        assertThrows(IllegalArgumentException.class,
                () -> new LibrarianBootstrapProperties(UNIVERSITY_ID, EMAIL, "Taylor", null, BCRYPT_HASH));
        assertThrows(IllegalArgumentException.class,
                () -> new LibrarianBootstrapProperties(
                        UNIVERSITY_ID, EMAIL, "Taylor", "Morgan", "plaintext"));
        assertFalse(userRepository.existsByUserRole(UserRole.LIBRARIAN));
    }

    /** Creates validated bootstrap properties without writing credentials to the repository. */
    private LibrarianBootstrapProperties configuredProperties() {
        return new LibrarianBootstrapProperties(
                UNIVERSITY_ID, EMAIL, "Taylor", "Morgan", BCRYPT_HASH);
    }

    /** Creates a fresh runner to model the application runner being invoked on a later start. */
    private LibrarianBootstrapRunner runner(LibrarianBootstrapProperties properties) {
        return new LibrarianBootstrapRunner(properties, userRepository);
    }
}
