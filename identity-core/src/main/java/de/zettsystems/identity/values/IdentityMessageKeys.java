package de.zettsystems.identity.values;

/**
 * i18n-Schlüssel der Meldungen, die dieser Baustein erzeugen kann.
 *
 * <p>Der Baustein löst sie nicht selbst auf — welche Sprachdateien es gibt,
 * weiß nur die einbindende Anwendung. Sie muss für jeden Schlüssel hier einen
 * Text pflegen.
 */
public final class IdentityMessageKeys {

    public static final String EMAIL_ALREADY_REGISTERED = "identity.error.emailAlreadyRegistered";
    public static final String PASSWORD_TOO_SHORT = "identity.error.passwordTooShort";
    public static final String SELF_REGISTRATION_DISABLED = "identity.error.selfRegistrationDisabled";
    public static final String TOKEN_INVALID = "identity.error.tokenInvalid";
    public static final String TOKEN_EXPIRED = "identity.error.tokenExpired";
    public static final String DEFAULT_ROLE_MISSING = "identity.error.defaultRoleMissing";
    public static final String ACCOUNT_NOT_FOUND = "identity.error.accountNotFound";
    /** Das Konto hat schon eine Adresse — es gehört bereits jemandem. */
    public static final String ACCOUNT_ALREADY_CLAIMED = "identity.error.accountAlreadyClaimed";
    /** Rückfall der Oberfläche für alles, was sie nicht einzeln behandelt. */
    public static final String UNEXPECTED = "identity.error.unexpected";

    private IdentityMessageKeys() {
        // Konstanten-Klasse
    }
}
