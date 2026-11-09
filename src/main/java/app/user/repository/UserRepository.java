package app.user.repository;

import app.user.entity.User;
import app.user.entity.enums.UserCourse;
import app.user.entity.enums.UserRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // used to find a specific user
    Optional<User> findByUniversityId(String universityId);

    /**
     * Loads and write-locks a borrower to serialize active-loan limit checks.
     *
     * @param universityId authenticated borrower's university ID
     * @return the locked user, if one exists
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.universityId = :universityId")
    Optional<User> findByUniversityIdForUpdate(@Param("universityId") String universityId);

    // used to find a specific user
    Optional<User> findByEmailIgnoreCase(String email);

    // checks to see if user is already registered
    boolean existsByUniversityId(String universityId);

    // checks to see fi user is already registered
    boolean existsByEmailIgnoreCase(String email);

    // finds users by role
    List<User> findByUserRole(UserRole userRole);

    /** Returns whether any account already has the supplied role. */
    boolean existsByUserRole(UserRole userRole);

    // Returns a pageable subset of users with one role.
    Page<User> findByUserRole(UserRole userRole, Pageable pageable);

    // Returns a paged directory limited to the account roles visible to staff.
    Page<User> findByUserRoleIn(Collection<UserRole> userRoles, Pageable pageable);

    /** Applies staff scope and optional case-insensitive directory search before pagination. */
    @Query("""
            SELECT u FROM User u WHERE u.userRole IN :roles
              AND (:role IS NULL OR u.userRole = :role)
              AND (LOCATE(LOWER(:q), LOWER(u.universityId)) > 0
                OR LOCATE(LOWER(:q), LOWER(u.firstName)) > 0
                OR LOCATE(LOWER(:q), LOWER(u.lastName)) > 0
                OR LOCATE(LOWER(:q), LOWER(u.email)) > 0)
            """)
    Page<User> searchDirectory(@Param("roles") Collection<UserRole> roles,
                               @Param("role") UserRole role, @Param("q") String q, Pageable pageable);

    /** Detects preserved borrowing history before an account is deleted. */
    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END FROM Loan l WHERE l.borrower.id = :id OR l.lostBy.id = :id")
    boolean hasLoanHistory(@Param("id") Long id);

    // finds users by course
    List<User> findByUserCourse(UserCourse userCourse);
}
