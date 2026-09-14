package app.user.entity;

import app.user.entity.enums.UserCourse;
import app.user.entity.enums.UserITMajor;
import app.user.entity.enums.UserRole;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "university_id",nullable = false, unique = true, length = 9)
    private String universityId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name must not exceed 50 characters")
    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name must not exceed 50 characters")
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Size(max = 1, message = "Middle initial must be one character")
    @Column(name = "middle_initial", length = 1)
    private String middleInitial;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role",nullable = false)
    private UserRole userRole;

    // Applies only to students
    @Enumerated(EnumType.STRING)
    @Column(name = "student_course")
    private UserCourse userCourse;

    // Applies only to I.T students
    @Enumerated(EnumType.STRING)
    @Column(name = "infotech_major")
    private UserITMajor major;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;




}
