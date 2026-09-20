# Libro Institutional Context

## Purpose and setting

Libro is a library management system prototype for Aklan State University – College of Computer Studies (ASU-CCS). This document records the institutional assumptions that shaped the user-domain foundation so they are available as project context without making the main README too broad.

## Alignment with the existing MIS

The existing university management information system uses the university ID as the username. Libro keeps that identifier while using its own password policy:

- University IDs use the `####-####` format.
- User creation and password changes require at least 8 characters, including uppercase and lowercase letters, a number, and a symbol.
- Passwords are encoded with BCrypt before persistence and are never returned in response DTOs.
- University IDs and email addresses are normalized before duplicate checks and storage.

This is alignment with the existing identity convention, not a claim that Libro currently authenticates against or synchronizes with the MIS. Libro does not yet have a user controller or completed authentication integration.

## CCS academic structure

The user-domain foundation represents the courses offered by the College of Computer Studies:

- Bachelor of Science in Entertainment and Multimedia Computing (EMC)
- Bachelor of Science in Information Technology (IT)
- Bachelor of Science in Information Systems (IS)

The majors represented under Information Technology are:

- Software Engineering (SE)
- Hardware and Networking (HN)
- Service Management and Business Process Outsourcing (SMBPO)
- Instructional Systems Technology (IST)

The service and database rules preserve the relationships between role, course, and major. For example, only IT students can have an IT major, while EMC and IS students and faculty members do not receive one.

## Why this belongs in the project reference

These conventions make the prototype easier to discuss as an institutional system rather than a generic CRUD exercise. They show where the data model comes from, which assumptions would matter during future MIS integration, and which parts remain deliberately incomplete before production use.
