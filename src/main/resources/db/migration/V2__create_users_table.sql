-- Create the users table only if it does not already exist.
CREATE TABLE IF NOT EXISTS users (
    -- Generate a unique numeric database identifier for each user.
    id BIGSERIAL PRIMARY KEY,

    -- Store the university ID such as 2025-4321 and prevent duplicates.
    university_id VARCHAR(9) NOT NULL UNIQUE,

    -- Store only the encoded password, never the original password or PIN.
    password_hash VARCHAR(255) NOT NULL,

    -- Store the user's family name.
    last_name VARCHAR(50) NOT NULL,

    -- Store the user's given name.
    first_name VARCHAR(50) NOT NULL,

    -- Store an optional one-character middle initial.
    middle_initial VARCHAR(1),

    -- Store the user's email address and prevent duplicate email addresses.
    email VARCHAR(150) NOT NULL UNIQUE,

    -- Store whether the user is a student or faculty member.
    user_role VARCHAR(20) NOT NULL,

    -- Store the student's course; this must be null for faculty members.
    student_course VARCHAR(10),

    -- Store an IT major; this must be null for non-IT students and faculty.
    infotech_major VARCHAR(10),

    -- Restrict user roles to the values represented by the Java UserRole enum.
    CONSTRAINT chk_user_role
        CHECK (user_role IN ('STUDENT', 'FACULTY')),

    -- Require a valid course for students and no course or major for faculty.
    CONSTRAINT chk_student_course
        CHECK (
            (user_role = 'FACULTY'
                AND student_course IS NULL
                AND infotech_major IS NULL)
            OR
            (user_role = 'STUDENT'
                AND student_course IN ('EMC', 'IS', 'IT'))
        ),

    -- Require an approved major for IT students and no major for EMC, IS, or faculty.
    CONSTRAINT chk_it_major
        CHECK (
            (user_role = 'STUDENT'
                AND student_course = 'IT'
                AND infotech_major IN ('SE', 'SMBPO', 'IST', 'HN'))
            OR
            (user_role = 'STUDENT'
                AND student_course IN ('EMC', 'IS')
                AND infotech_major IS NULL)
            OR
            (user_role = 'FACULTY'
                AND infotech_major IS NULL)
        ),

    -- Require university IDs to follow the four-digits-hyphen-four-digits format.
    CONSTRAINT chk_university_id_format
        CHECK (university_id ~ '^[0-9]{4}-[0-9]{4}$')
);

-- Speed up searches and filtering by last name.
CREATE INDEX idx_users_last_name ON users(last_name);

-- Speed up searches and filtering by first name.
CREATE INDEX idx_users_first_name ON users(first_name);

-- Speed up filtering by student course.
CREATE INDEX idx_users_course ON users(student_course);

-- Speed up filtering by student or faculty role.
CREATE INDEX idx_users_role ON users(user_role);
