package unit.auth;

import app.auth.config.JwtConfiguration;
import app.auth.config.JwtProperties;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Unit tests for the startup-time JWT signing-key boundary. */
class JwtConfigurationTest {

    private final JwtConfiguration configuration = new JwtConfiguration();

    /** Ensures a valid 32-byte Base64 key is decoded without changing its bytes. */
    @Test
    void acceptsMinimumLengthKey() {
        byte[] bytes = new byte[32];
        JwtProperties properties = properties(Base64.getEncoder().encodeToString(bytes));

        assertEquals(32, configuration.jwtSecretKey(properties).getEncoded().length);
    }

    /** Ensures malformed Base64 stops application startup. */
    @Test
    void rejectsMalformedBase64Key() {
        assertThrows(IllegalStateException.class, () -> configuration.jwtSecretKey(properties("not-base64!")));
    }

    /** Ensures decoded key material shorter than 256 bits is not accepted. */
    @Test
    void rejectsShortKey() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[31]);

        assertThrows(IllegalStateException.class, () -> configuration.jwtSecretKey(properties(shortKey)));
    }

    /** Builds settings around the supplied secret without loading a Spring context. */
    private JwtProperties properties(String secret) {
        return new JwtProperties("libro-api", "libro-api", secret, 900);
    }
}
