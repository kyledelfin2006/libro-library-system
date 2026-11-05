package app.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Optional operator-supplied values for creating the first librarian at startup.
 * Each value defaults to blank and maps to its same-named {@code LIBRO_BOOTSTRAP_LIBRARIAN_*} environment variable;
 * all values must be present together, and the password input must already be a BCrypt hash.
 */
@ConfigurationProperties(prefix = "libro.bootstrap.librarian")
public record LibrarianBootstrapProperties(
        String universityId,
        String email,
        String firstName,
        String lastName,
        String passwordHash
) {

    private static final Pattern BCRYPT_HASH = Pattern.compile("^\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}$");

    /**
     * Trims optional values, rejects partial configuration, and permits only the database's BCrypt hash format.
     *
     * @param universityId optional 0000-0000-format ID from LIBRO_BOOTSTRAP_LIBRARIAN_UNIVERSITY_ID; blank default disables provisioning
     * @param email optional email from LIBRO_BOOTSTRAP_LIBRARIAN_EMAIL; blank default disables provisioning
     * @param firstName optional first name from LIBRO_BOOTSTRAP_LIBRARIAN_FIRST_NAME; blank default disables provisioning
     * @param lastName optional last name from LIBRO_BOOTSTRAP_LIBRARIAN_LAST_NAME; blank default disables provisioning
     * @param passwordHash optional BCrypt hash from LIBRO_BOOTSTRAP_LIBRARIAN_PASSWORD_HASH; blank default disables provisioning
     * @throws IllegalArgumentException if only some values are supplied or the hash is not BCrypt
     */
    public LibrarianBootstrapProperties {
        universityId = trimToNull(universityId);
        email = trimToNull(email);
        firstName = trimToNull(firstName);
        lastName = trimToNull(lastName);
        passwordHash = trimToNull(passwordHash);

        boolean anyValue = universityId != null || email != null || firstName != null
                || lastName != null || passwordHash != null;
        boolean allValues = universityId != null && email != null && firstName != null
                && lastName != null && passwordHash != null;
        if (anyValue && !allValues) {
            throw new IllegalArgumentException("All librarian bootstrap values must be supplied together");
        }
        if (allValues && !BCRYPT_HASH.matcher(passwordHash).matches()) {
            throw new IllegalArgumentException("Librarian bootstrap password must be a BCrypt hash");
        }
        if (allValues && !universityId.matches("[0-9]{4}-[0-9]{4}")) {
            throw new IllegalArgumentException("Librarian bootstrap university ID must use the 0000-0000 format");
        }
        // Match the users table limits so a bad value fails at startup with a clear message, not at insert time.
        if (email != null && (email.length() > 150 || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))) {
            throw new IllegalArgumentException("Librarian bootstrap email must be valid and at most 150 characters");
        }
        if ((firstName != null && firstName.length() > 50) || (lastName != null && lastName.length() > 50)) {
            throw new IllegalArgumentException("Librarian bootstrap names must be at most 50 characters");
        }
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Redacts the password hash so accidental logging of this record cannot leak credential material.
     *
     * @return a representation that omits account data
     */
    @Override
    public String toString() {
        return "LibrarianBootstrapProperties[configured=" + configured() + "]";
    }

    /**
     * Reports whether the operator supplied the complete bootstrap tuple.
     *
     * @return true when all bootstrap values are present; false when provisioning is disabled
     */
    public boolean configured() {
        return universityId != null;
    }

    /**
     * Trims a configuration value and treats blank input as absent so unset environment variables disable bootstrap.
     *
     * @param value raw configuration value
     * @return trimmed nonblank value, or null when unset
     */
    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
