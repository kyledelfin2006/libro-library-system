package app.user.validation;

import java.nio.charset.StandardCharsets;

/**
 * Shared password-validation rules for user creation and password changes.
 */
public final class PasswordPolicy {

    /**
     * Requires a password of 8 to 72 characters containing an uppercase
     * letter, a lowercase letter, a digit, and a non-whitespace symbol. Each
     * lookahead checks for one required character type before the final length
     * check accepts the complete password. The upper bound limits unnecessary
     * BCrypt work; {@link #isWithinBcryptByteLimit(String)} separately enforces
     * BCrypt's actual UTF-8 input boundary.
     */
    public static final String PASSWORD_REGEX =
            "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,72}$";

    /** Maximum password length accepted before BCrypt processing. */
    public static final int MAX_PASSWORD_LENGTH = 72;

    /** Maximum UTF-8 byte length accepted by BCrypt. */
    public static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    /** Plain-language validation feedback shared by all password inputs. */
    public static final String PASSWORD_REQUIREMENTS_MESSAGE =
            "Password must be 8 to 72 characters and include uppercase and lowercase "
                    + "letters, a number, and a symbol";

    /**
     * Checks the encoded password size before passing the value to BCrypt.
     *
     * @param password raw password value
     * @return true when the UTF-8 input is within BCrypt's 72-byte boundary
     */
    public static boolean isWithinBcryptByteLimit(String password) {
        return password != null
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BCRYPT_PASSWORD_BYTES;
    }

    private PasswordPolicy() {
        // Utility class; password rules are accessed through its constants.
    }
}
