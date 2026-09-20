package app.user.validation;

/**
 * Shared password-validation rules for user creation and password changes.
 */
public final class PasswordPolicy {

    /**
     * Requires a password of at least eight characters containing an uppercase
     * letter, a lowercase letter, a digit, and a non-whitespace symbol. Each
     * lookahead checks for one required character type before the final length
     * check accepts the complete password.
     */
    public static final String PASSWORD_REGEX =
            "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,}$";

    /** Plain-language validation feedback shared by all password inputs. */
    public static final String PASSWORD_REQUIREMENTS_MESSAGE =
            "Password must be at least 8 characters and include uppercase and lowercase "
                    + "letters, a number, and a symbol";

    private PasswordPolicy() {
        // Utility class; password rules are accessed through its constants.
    }
}
