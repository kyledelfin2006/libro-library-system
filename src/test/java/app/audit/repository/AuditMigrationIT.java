package app.audit.repository;

import app.LibraryApplication;
import app.audit.entity.AuditEvent;
import app.audit.service.AuditService;
import app.user.entity.enums.UserRole;
import app.support.PostgresTestConfig;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.PageRequest;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Validates the V6-to-V7 upgrade without altering existing migrations or data. */
@ActiveProfiles("test")
@SpringBootTest(classes = LibraryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(PostgresTestConfig.class)
@ResourceLock("postgres")
class AuditMigrationIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway applicationFlyway;
    @Autowired AuditRepository events;
    @Autowired AuditService audit;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void rolledBackChangeLeavesNoAuditEvent() {
        long before = events.count();
        new TransactionTemplate(transactions).execute(status -> {
            audit.record("BOOK_CHANGED", "BOOK", "999999", null);
            status.setRollbackOnly();
            return null;
        });
        assertEquals(before, events.count());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void assistantScopeIsAppliedBeforeAuditPageCount() {
        var now = app.global.time.LibraryTime.now();
        events.save(new AuditEvent(now, "2099-8888", "LIBRARIAN", UserRole.ADMINISTRATOR,
                "PASSWORD_RESET", "USER", "2025-8765", "password reset"));
        events.save(new AuditEvent(now, "2099-8888", "LIBRARIAN", UserRole.STUDENT,
                "PASSWORD_RESET", "USER", "2025-9999", "password reset"));
        events.save(new AuditEvent(now, "2099-8888", "LIBRARIAN", null,
                "BOOK_CHANGED", "BOOK", "999999", "book changed"));
        var assistant = events.search(false, java.util.List.of(UserRole.STUDENT, UserRole.FACULTY),
                "2099-8888", null, null, null, null, null, PageRequest.of(0, 12));
        var librarian = events.search(true, java.util.List.of(UserRole.STUDENT, UserRole.FACULTY),
                "2099-8888", null, null, null, null, null, PageRequest.of(0, 12));
        assertEquals(2, assistant.getTotalElements());
        assertEquals(3, librarian.getTotalElements());
    }

    @Test
    void v6ToV7PreservesAccountsAndValidatesAuditMapping() {
        String schema = "audit_v7_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var source = java.util.Objects.requireNonNull(jdbc.getDataSource());
        var placeholders = applicationFlyway.getConfiguration().getPlaceholders();
        Flyway v6 = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .target(MigrationVersion.fromVersion("6")).placeholders(placeholders).load();
        Flyway v7 = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .target(MigrationVersion.fromVersion("7")).placeholders(placeholders).load();
        try {
            v6.migrate();
            jdbc.update("INSERT INTO " + schema + ".users "
                    + "(university_id, password_hash, first_name, last_name, email, user_role, student_course) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    "2099-8888", "test-only-hash", "Sample", "Patron", "patron@example.test", "STUDENT", "IS");
            v7.migrate();
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM " + schema
                    + ".users WHERE university_id = '2099-8888'", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + schema + ".audit_event", Integer.class));
            // Hibernate validation checks all audit columns against the freshly upgraded schema.
            var registry = new StandardServiceRegistryBuilder()
                    .applySetting("hibernate.connection.datasource", source)
                    .applySetting("hibernate.default_schema", schema)
                    .applySetting("hibernate.hbm2ddl.auto", "validate").build();
            try (var ignored = new MetadataSources(registry).addAnnotatedClass(AuditEvent.class)
                    .buildMetadata().buildSessionFactory()) {
                // Building the factory performs schema validation.
            } finally {
                StandardServiceRegistryBuilder.destroy(registry);
            }
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}
