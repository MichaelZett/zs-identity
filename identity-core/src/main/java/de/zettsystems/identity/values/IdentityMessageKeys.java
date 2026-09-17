package de.zettsystems.identity.values;

/**
 * The i18n keys of every message this building block can produce.
 *
 * <p>The building block does not resolve them itself: only the embedding
 * application knows which message bundles exist. It has to provide a text for
 * every key listed here.
 */
public final class IdentityMessageKeys {

    public static final String EMAIL_ALREADY_REGISTERED = "identity.error.emailAlreadyRegistered";
    public static final String PASSWORD_TOO_SHORT = "identity.error.passwordTooShort";
    public static final String SELF_REGISTRATION_DISABLED = "identity.error.selfRegistrationDisabled";
    public static final String TOKEN_INVALID = "identity.error.tokenInvalid";
    public static final String TOKEN_EXPIRED = "identity.error.tokenExpired";
    public static final String DEFAULT_ROLE_MISSING = "identity.error.defaultRoleMissing";
    public static final String ACCOUNT_NOT_FOUND = "identity.error.accountNotFound";
    /** The account already has an address, so it already belongs to someone. */
    public static final String ACCOUNT_ALREADY_CLAIMED = "identity.error.accountAlreadyClaimed";
    /** The UI's fallback for everything it does not handle individually. */
    public static final String UNEXPECTED = "identity.error.unexpected";

    private IdentityMessageKeys() {
        // Constants class
    }
}
