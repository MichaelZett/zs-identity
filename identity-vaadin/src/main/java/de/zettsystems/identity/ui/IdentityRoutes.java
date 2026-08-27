package de.zettsystems.identity.ui;

/**
 * Die Pfade der Identity-Ansichten an einer Stelle.
 *
 * <p>Die einbindende Anwendung braucht sie für die Spring-Security-Konfiguration
 * und für Verweise aus dem eigenen Menü; die Dienste im Kern bauen daraus die
 * Links in den Mails. Als Konstanten, damit ein Pfadwechsel nicht an drei
 * Stellen gleichzeitig gepflegt werden muss.
 */
public final class IdentityRoutes {

    public static final String LOGIN = "login";
    public static final String REGISTER = "register";
    public static final String CONFIRM_EMAIL = "register/confirm";
    public static final String RESEND_VERIFICATION = "register/resend";
    public static final String FORGOT_PASSWORD = "password/forgot";
    public static final String RESET_PASSWORD = "password/reset";

    /** Parametername des Tokens in Bestätigungs- und Reset-Links. */
    public static final String TOKEN_PARAMETER = "token";

    private IdentityRoutes() {
        // Konstanten-Klasse
    }
}
