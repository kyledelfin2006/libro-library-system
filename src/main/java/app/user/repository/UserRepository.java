package app.user.repository;

import app.user.entity.User;
import app.user.entity.enums.UserCourse;
import app.user.entity.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // used to find a specific user
    Optional<User> findByUniversityId(String universityId);

    // used to find a specific user
    Optional<User> findByEmailIgnoreCase(String email);

    // checks to see if user is already registered
    boolean existsByUniversityId(String universityId);

    // checks to see fi user is already registered
    boolean existsByEmailIgnoreCase(String email);

    // finds users by role
    List<User> findByUserRole(UserRole userRole);

    // finds users by course
    List<User> findByUserCourse(UserCourse userCourse);
}