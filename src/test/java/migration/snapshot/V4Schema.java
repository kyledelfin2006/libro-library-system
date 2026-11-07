package migration.snapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Version-matched mappings for validating V4 outside the application's V5 entity scan. */
public final class V4Schema {
    private V4Schema() {
    }

    /** The V4 catalog still names its monetary column price. */
    @Entity
    @Table(name = "books")
    public static class Book {
        @Id
        Long id;
        @Column(name = "price", precision = 10, scale = 2)
        BigDecimal price;
        @Column(name = "copy_status", length = 24)
        String copyStatus;
    }

    /** V4 retains the V3 account table. */
    @Entity
    @Table(name = "users")
    public static class User {
        @Id
        Long id;
        @Column(name = "university_id", length = 9)
        String universityId;
    }

    /** V4 introduces the loan table with references and a lifecycle status. */
    @Entity
    @Table(name = "loans")
    public static class Loan {
        @Id
        Long id;
        @Column(name = "user_id")
        Long userId;
        @Column(name = "book_id")
        Long bookId;
        @Column(name = "status", length = 16)
        String status;
    }
}
