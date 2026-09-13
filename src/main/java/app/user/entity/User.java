package app.user.entity;

import app.user.entity.enums.UserCourse;
import app.user.entity.enums.UserITMajor;
import app.user.entity.enums.UserRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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




}
