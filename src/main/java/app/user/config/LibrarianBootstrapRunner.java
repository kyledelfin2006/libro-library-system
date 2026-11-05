package app.user.config;

import app.user.entity.User;
import app.user.entity.enums.UserRole;
import app.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Creates the first librarian from complete operator configuration after the database is ready.
 * This startup-only path is idempotent and never logs account data or the configured BCrypt hash.
 */
@Slf4j
@Component
@EnableConfigurationProperties(LibrarianBootstrapProperties.class)
public class LibrarianBootstrapRunner implements ApplicationRunner {

    private final LibrarianBootstrapProperties properties;
    private final UserRepository userRepository;

    /**
     * Creates the bootstrap runner with validated optional configuration and the user persistence boundary.
     *
     * @param properties optional all-or-nothing librarian identity and BCrypt hash
     * @param userRepository user account persistence
     */
    public LibrarianBootstrapRunner(
            LibrarianBootstrapProperties properties,
            UserRepository userRepository) {
        this.properties = properties;
        this.userRepository = userRepository;
    }

    /**
     * Creates the configured librarian only when no librarian account exists already.
     *
     * @param args application startup arguments (unused)
     */
    @Override
    public void run(ApplicationArguments args) {
        // Leave existing deployments unchanged when the operator did not opt in with every value.
        if (!properties.configured()) {
            return;
        }

        // Make repeated application starts idempotent once any librarian account exists.
        if (userRepository.existsByUserRole(UserRole.LIBRARIAN)) {
            return;
        }

        // Build a staff account with no student-only course or major fields.
        User librarian = new User();
        librarian.setUniversityId(properties.universityId());
        librarian.setEmail(properties.email());
        librarian.setFirstName(properties.firstName());
        librarian.setLastName(properties.lastName());
        librarian.setPasswordHash(properties.passwordHash());
        librarian.setUserRole(UserRole.LIBRARIAN);
        librarian.setUserCourse(null);
        librarian.setMajor(null);

        // Persist through the repository and log only a generic completion message.
        userRepository.save(librarian);
        log.info("First librarian account provisioned from startup configuration");
    }
}
