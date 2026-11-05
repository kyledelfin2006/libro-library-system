package integration;

import app.LibraryApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the actual Actuator routes through the running application and PostgreSQL-backed readiness check. */
@SpringBootTest(classes = LibraryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
@ResourceLock("postgres")
class HealthEndpointIT {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    /** Confirms anonymous liveness/readiness probes return minimal UP responses and other Actuator paths stay private. */
    @Test
    void exposesOnlyMinimalAnonymousHealthProbes() throws Exception {
        for (String path : new String[]{"/actuator/health/liveness", "/actuator/health/readiness"}) {
            HttpResponse<String> response = get(path);

            // Plain string checks avoid depending on a particular Jackson major version in this test.
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"status\":\"UP\""));
            assertFalse(response.body().contains("components"));
        }

        assertEquals(401, get("/actuator/env").statusCode());
    }

    /** Sends an anonymous GET request to the random local Tomcat port for the current integration-test app. */
    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
