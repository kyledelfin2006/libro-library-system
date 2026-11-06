package app.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts one real PostgreSQL container for the {@code *IT} suite and wires it into Spring.
 * <p>
 * {@code @ServiceConnection} makes Spring Boot read the container's JDBC URL and credentials directly, so no
 * datasource properties are needed in the test profile.
 * </p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

    /**
     * Same image and digest as the {@code db} service in docker-compose.yml, so integration tests run against the
     * exact PostgreSQL build the application ships with. When Dependabot bumps the Compose digest, update it here too.
     */
    static final String POSTGRES_IMAGE =
            "postgres:18-alpine@sha256:123b17998ccaf47e53b502e8c6a1e79afd8f579c6f3eec83c563da026f957395";

    /**
     * Declares the container as a bean so its lifecycle follows the Spring test context.
     *
     * @return the PostgreSQL container used by every integration test context
     */
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // A tag-plus-digest reference fails Testcontainers' name check, so declare it as the official postgres image.
        return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE).asCompatibleSubstituteFor("postgres"));
    }
}
