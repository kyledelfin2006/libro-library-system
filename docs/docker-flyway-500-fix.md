# Docker, Flyway, and Hibernate 500-error runbook

## Symptom

Swagger UI can appear reachable while API calls fail, and `docker compose ps` may show `library-app` repeatedly restarting. Swagger's static browser assets are not proof that the current application process completed startup or can query PostgreSQL.

Inspect the application first:

```powershell
docker compose ps
docker compose logs --tail=200 app
```

The failure addressed by this runbook contains:

```text
Schema validation: wrong column type encountered in column [id] in table [books];
found [serial (Types#INTEGER)], but expecting [bigint (Types#BIGINT)]
```

## Root cause

Four configuration problems combined:

1. `Book.id` is a Java `Long`. Hibernate therefore validates it as SQL `BIGINT`, but V1 originally created `books.id` as PostgreSQL `SERIAL`, which is an `INTEGER` identity column.
2. Spring Boot 4 modularized Flyway support. Having `org.flywaydb:flyway-core` on the classpath is not enough to activate Boot's migration auto-configuration; the application needs `org.springframework.boot:spring-boot-starter-flyway`. The missing starter explains why startup had no Flyway log lines and PostgreSQL had no `flyway_schema_history` table.
3. Compose mounted V1 into `/docker-entrypoint-initdb.d` while the application also claimed Flyway managed the schema. PostgreSQL init scripts run only for a new data directory and are not migration tracking. This produced a non-empty schema unknown to Flyway.
4. The PostgreSQL 18 image moved its declared volume from `/var/lib/postgresql/data` to `/var/lib/postgresql`. Keeping the older target can put the real version-specific `PGDATA` outside the intended named volume.

The database connection itself was healthy. Hibernate reached PostgreSQL 18 and deliberately stopped startup because `spring.jpa.hibernate.ddl-auto=validate` detected the incompatible schema.

## Implemented fix

- Replaced direct `flyway-core` usage with `spring-boot-starter-flyway` and retained the PostgreSQL Flyway module.
- Renamed migration files to Flyway's required `V<version>__<description>.sql` convention.
- Corrected the undeployed V1 migration to use `BIGSERIAL`, matching Java `Long` from the initial schema version.
- Removed the PostgreSQL init-script bind mount, making Flyway the only schema owner.
- Mounted `postgres_data` at PostgreSQL 18's `/var/lib/postgresql` volume root.
- Added a PostgreSQL health check and made the app wait for it.
- Explicitly configured Flyway's `classpath:db/migration` location and left automatic baselining disabled.
- Kept Hibernate schema validation enabled so future entity/migration drift fails during startup instead of causing request-time data errors.

Changing V1 is appropriate here only because the system is unfinished and has no persistent application data or deployed Flyway history. After release, never edit an applied migration; add a forward migration instead.

## Apply the repair

The current development database is explicitly empty, so recreate it to discard the obsolete unmanaged schema:

```powershell
mvn clean package
docker compose down -v
docker compose up -d --build
docker compose logs --tail=200 app
```

`docker compose down -v` deletes the PostgreSQL volume. Use this pre-release repair only while it is confirmed empty. If any environment contains data, back it up and create a new forward migration instead.

Expected startup evidence includes:

```text
Migrating schema "public" to version "1 - create books table"
Migrating schema "public" to version "2 - create users table"
Initialized JPA EntityManagerFactory
Started LibraryApplication
```

The Flyway history should contain V1 and V2 with no baseline row.

## Verify

```powershell
docker compose ps
curl.exe -i http://localhost:8080/app/books/health
curl.exe -i http://localhost:8080/app/books/all
docker compose exec db psql -U <POSTGRES_USER> -d <POSTGRES_DB> -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
docker compose exec db psql -U <POSTGRES_USER> -d <POSTGRES_DB> -c "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'books' AND column_name = 'id';"
```

The app should remain `Up`, both HTTP requests should return 200, all Flyway rows should have `success = true`, and `books.id` should report `bigint`.

## If it still fails

- Confirm the image contains the newly built jar. The Dockerfile copies `target/*.jar`, so `docker compose up --build` alone does not compile changed Java or resources.
- Search the logs for `org.flywaydb`. No Flyway output usually means an old jar/image or a missing `spring-boot-starter-flyway` dependency.
- Do not set `ddl-auto=update` to suppress validation. That mixes Hibernate schema mutation with Flyway and conceals migration drift.
- Do not manually edit `flyway_schema_history` or an already-applied migration. Add a new forward migration.
- Do not enable `baseline-on-migrate` merely to bypass a non-empty-schema error. Determine whether that schema contains data and create a proper adoption plan.
