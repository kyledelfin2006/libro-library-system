package app.loan.repository;

import app.LibraryApplication;
import app.support.PostgresTestConfig;
import migration.snapshot.V4Schema;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** PostgreSQL upgrade coverage for existing V3 account and catalog data. */
@ActiveProfiles("test")
@SpringBootTest(classes = LibraryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(PostgresTestConfig.class)
@ResourceLock("postgres")
class LoanMigrationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway applicationFlyway;

    /** Proves V4 adds loans and copy state while retaining preexisting V3 rows and invariants. */
    @Test
    void v3ToV4PreservesUsersAndBooksAndEnforcesLoanState() {
        String schema = "loan_v4_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var dataSource = java.util.Objects.requireNonNull(jdbcTemplate.getDataSource());
        var placeholders = applicationFlyway.getConfiguration().getPlaceholders();
        Flyway v3 = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target(MigrationVersion.fromVersion("3")).placeholders(placeholders).load();
        Flyway v4 = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target(MigrationVersion.fromVersion("4")).placeholders(placeholders).load();

        try {
            v3.migrate();
            // Seed a patron and physical copy in the isolated pre-loan schema.
            jdbcTemplate.update("INSERT INTO " + schema + ".users "
                    + "(university_id, password_hash, first_name, last_name, email, user_role, student_course) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    "2025-4321", "test-only-hash", "Sample", "Patron", "patron@example.test", "STUDENT", "IS");
            jdbcTemplate.update("INSERT INTO " + schema + ".books (title, author, genre, price) VALUES (?, ?, ?, ?)",
                    "Existing copy", "Test author", "Fiction", new BigDecimal("19.99"));
            assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = ? AND table_name = 'loans'", Integer.class, schema));

            v4.migrate();

            // Validate the V4 schema with version-matched mappings; the production entities target V5.
            var registry = new StandardServiceRegistryBuilder()
                    .applySetting("hibernate.connection.datasource", dataSource)
                    .applySetting("hibernate.default_schema", schema)
                    .applySetting("hibernate.hbm2ddl.auto", "validate")
                    .build();
            try (var factory = new MetadataSources(registry)
                    .addAnnotatedClass(V4Schema.Book.class)
                    .addAnnotatedClass(V4Schema.User.class)
                    .addAnnotatedClass(V4Schema.Loan.class)
                    .buildMetadata().buildSessionFactory()) {
                // Opening the factory runs Hibernate's schema validator.
            } finally {
                StandardServiceRegistryBuilder.destroy(registry);
            }

            assertEquals("IN_COLLECTION", jdbcTemplate.queryForObject(
                    "SELECT copy_status FROM " + schema + ".books WHERE title = 'Existing copy'", String.class));
            assertEquals(0, new BigDecimal("19.99").compareTo(jdbcTemplate.queryForObject(
                    "SELECT price FROM " + schema + ".books WHERE title = 'Existing copy'", BigDecimal.class)));
            assertEquals("Sample", jdbcTemplate.queryForObject(
                    "SELECT first_name FROM " + schema + ".users WHERE university_id = '2025-4321'", String.class));
            assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = ? AND table_name = 'loans'", Integer.class, schema));
            assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pg_indexes "
                    + "WHERE schemaname = ? AND indexname = 'uq_loans_one_active_per_book' "
                    + "AND indexdef LIKE '%WHERE%status%ACTIVE%'", Integer.class, schema));
            assertEquals(5, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pg_constraint "
                    + "WHERE connamespace = ?::regnamespace AND conname IN "
                    + "('chk_loan_status', 'chk_loan_due_after_borrowed', 'chk_loan_return_after_borrowed', "
                    + "'chk_loan_lost_after_borrowed', 'chk_loan_state_fields')", Integer.class, schema));

            // A RETURNED row without returned_at must violate the state-fields check.
            assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                    "INSERT INTO " + schema + ".loans (user_id, book_id, borrowed_at, due_at, status) "
                            + "VALUES ((SELECT id FROM " + schema + ".users WHERE university_id = '2025-4321'), "
                            + "(SELECT id FROM " + schema + ".books WHERE title = 'Existing copy'), "
                            + "TIMESTAMP '2026-10-01 10:00:00', TIMESTAMP '2026-10-15 10:00:00', 'RETURNED')"));
        } finally {
            // Drop only the uniquely named schema used by this test.
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

}
