# Local Setup and Configuration

## Docker (recommended)

Docker is the preferred way to run the project because it brings up both PostgreSQL 18 and the Spring Boot application together.

1. Create your environment file from the example:
    ```powershell
    Copy-Item .env.example .env
    ```
    Use values like:
    ```env
    POSTGRES_DB=librarydb
    POSTGRES_USER=admin
    POSTGRES_PASSWORD=change_me
    LIBRO_ADMIN_PASSWORD_HASH=<BCrypt hash for a password you choose>
    LIBRO_JWT_SECRET_BASE64=<output of openssl rand -base64 32>
    ```
    Replace both placeholders before starting the app. Flyway V2 seeds the historical `ADMINISTRATOR` account (library-assistant role) as university ID `0000-0001`; no librarian is seeded by migration. For the first librarian, optionally set all five `LIBRO_BOOTSTRAP_LIBRARIAN_*` values in `.env.example`, including a BCrypt hash, before startup. The application creates that account once and ignores the bootstrap values after any librarian exists. Keep `.env` private.
2. Start the full stack; Docker builds the JAR from a clean checkout:
    ```bash
    docker compose up --build
    ```
    To terminate TLS in front of the API, add the optional Caddy proxy profile, set `LIBRO_DOMAIN` and `SERVER_FORWARD_HEADERS_STRATEGY=native` in `.env`, and start with `docker compose --profile proxy up --build`. The proxy listens on ports 80 and 443 and is the only public entry point; the app and database stay on `127.0.0.1`. See [`infra/caddy/Caddyfile`](../../infra/caddy/Caddyfile).
3. Check `http://localhost:8080/readyz` without a token. Log in through `POST /app/auth/login`, then send the returned token as a Bearer credential on protected API requests. Swagger UI and `/v3/api-docs` are served only when `LIBRO_API_DOCS_ENABLED=true`; the `.env.example` template leaves it `false`, so set it to `true` in your local `.env` to use Swagger UI.

Set `LIBRO_CORS_ALLOWED_ORIGINS` to a comma-separated list of exact UI origins to enable browser access; it defaults to blank and wildcards are rejected. Set `SERVER_FORWARD_HEADERS_STRATEGY=native` only when the app port is reachable exclusively from the trusted OCI load balancer or proxy. The default is `none`.

## Local development

If you prefer to run the application directly on the host machine, start only PostgreSQL with Docker and then run Spring Boot locally.

1. Start the database:
    ```powershell
    docker compose up -d db
    ```
2. Set the datasource environment variables:
    ```powershell
    $env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/librarydb"
    $env:SPRING_DATASOURCE_USERNAME="admin"
    $env:SPRING_DATASOURCE_PASSWORD="change_me"
    $env:LIBRO_ADMIN_PASSWORD_HASH="<BCrypt hash for the local bootstrap account>"
    $env:LIBRO_JWT_SECRET_BASE64="<output of openssl rand -base64 32>"
    ```
    Maven does not read Docker Compose's `.env` file. Set the datasource variables, `LIBRO_ADMIN_PASSWORD_HASH`, and `LIBRO_JWT_SECRET_BASE64` in the process environment used to run the app. Issuer, audience, and token lifetime have defaults; the JWT secret must decode to at least 32 random bytes.
3. Run the app:
    ```bash
    ./mvnw spring-boot:run
    ```
