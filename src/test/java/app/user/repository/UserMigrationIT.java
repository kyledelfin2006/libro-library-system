package app.user.repository;

import app.LibraryApplication;
import app.support.PostgresTestConfig;
import app.user.entity.User;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the existing-account upgrade to the password-recovery schema on PostgreSQL. */
@ActiveProfiles("test")
@SpringBootTest(classes = LibraryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(PostgresTestConfig.class)
@ResourceLock("postgres")
class UserMigrationIT {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Flyway applicationFlyway;

    /** V6 keeps existing users and defaults their recovery flag to false. */
    @Test
    void v5ToV6PreservesAccountsAndValidatesJpaMapping() {
        String schema = "user_v6_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var dataSource = java.util.Objects.requireNonNull(jdbcTemplate.getDataSource());
        var placeholders = applicationFlyway.getConfiguration().getPlaceholders();
        Flyway v5 = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target(MigrationVersion.fromVersion("5")).placeholders(placeholders).load();
        Flyway v6 = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target(MigrationVersion.fromVersion("6")).placeholders(placeholders).load();
        try {
            v5.migrate();
            // A pre-upgrade account has no recovery column; migration must retain it with false.
            jdbcTemplate.update("INSERT INTO " + schema + ".users "
                            + "(university_id, password_hash, first_name, last_name, email, user_role, student_course) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    "2025-4321", "test-only-hash", "Sample", "Patron", "patron@example.test", "STUDENT", "IS");
            v6.migrate();
            assertFalse(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "SELECT must_change_password FROM " + schema + ".users WHERE university_id = '2025-4321'",
                    Boolean.class)));
            jdbcTemplate.update("UPDATE " + schema + ".users SET must_change_password = true "
                    + "WHERE university_id = '2025-4321'");
            assertTrue(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "SELECT must_change_password FROM " + schema + ".users WHERE university_id = '2025-4321'",
                    Boolean.class)));

            // Hibernate validate checks the new JPA field against the upgraded database column.
            var registry = new StandardServiceRegistryBuilder()
                    .applySetting("hibernate.connection.datasource", dataSource)
                    .applySetting("hibernate.default_schema", schema)
                    .applySetting("hibernate.hbm2ddl.auto", "validate")
                    .build();
            try (var ignored = new MetadataSources(registry).addAnnotatedClass(User.class)
                    .buildMetadata().buildSessionFactory()) {
                // Building the factory runs schema validation.
            } finally {
                StandardServiceRegistryBuilder.destroy(registry);
            }
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}
