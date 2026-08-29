package de.zettsystems.identity.values;

/**
 * Die Pfade der Identity-Ansichten, relativ zum Kontextpfad und ohne
 * führenden Schrägstrich.
 *
 * <p>Sie liegen im Kern, weil die Dienste daraus die Links in den Mails bauen
 * und weil auch eine REST-Anwendung ohne {@code identity-vaadin} die Pfade
 * für ihre Sicherheitskonfiguration braucht. Die Vaadin-Ansichten verweisen
 * über {@code IdentityRoutes} auf dieselben Konstanten, damit ein Pfadwechsel
 * nur an einer Stelle gepflegt wird.
 */
public final class IdentityPaths {

    public static final String LOGIN = "login";
    public static final String REGISTER = "register";
    public static final String CONFIRM_EMAIL = "register/confirm";
    public static final String RESEND_VERIFICATION = "register/resend";
    public static final String FORGOT_PASSWORD = "password/forgot";
    public static final String RESET_PASSWORD = "password/reset";
    /** Passwort ändern für angemeldete Konten — Ziel des erzwungenen Wechsels. */
    public static final String CHANGE_PASSWORD = "password/change";

    /** Parametername des Tokens in Bestätigungs- und Reset-Links. */
    public static final String TOKEN_PARAMETER = "token";

    private IdentityPaths() {
        // Konstanten-Klasse
    }
}
