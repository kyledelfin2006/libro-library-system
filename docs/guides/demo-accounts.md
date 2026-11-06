# Demo Accounts

These credentials are for local demos only. They are seeded from BCrypt hashes in a private `.env`; the hashes are not in the repository. **Never use these passwords or IDs in a real deployment.**

| Role | University ID | Password | Notes |
| --- | --- | --- | --- |
| LIBRARIAN | `0000-0000` | `Librarian@0000` | Created by the opt-in `LIBRO_BOOTSTRAP_LIBRARIAN_*` startup runner |
| ADMINISTRATOR (library assistant) | `0000-0001` | `Assistant@0001` | Created by Flyway V2 from `LIBRO_ADMIN_PASSWORD_HASH` |

Students sign up publicly at `POST /app/users/signup`; the librarian creates faculty and library-assistant accounts.

To seed them yourself, generate BCrypt hashes of the passwords above (single-quote them in `.env`, since hashes contain `$`) and set `LIBRO_ADMIN_PASSWORD_HASH` and the five `LIBRO_BOOTSTRAP_LIBRARIAN_*` variables (see `.env.example`).
