package de.zettsystems.identity.ui;

import de.zettsystems.identity.values.IdentityPaths;

/**
 * Die Pfade der Identity-Ansichten an einer Stelle.
 *
 * <p>Die einbindende Anwendung braucht sie für die Spring-Security-Konfiguration
 * und für Verweise aus dem eigenen Menü. Die Werte stammen aus
 * {@link IdentityPaths} im Kern, wo die Dienste daraus die Links in den Mails
 * bauen; hier nur der Vaadin-nahe Zugriff, damit die Ansichten und die
 * einbindende Anwendung keinen zweiten Satz Konstanten pflegen.
 */
public final class IdentityRoutes {

    public static final String LOGIN = IdentityPaths.LOGIN;
    public static final String REGISTER = IdentityPaths.REGISTER;
    public static final String CONFIRM_EMAIL = IdentityPaths.CONFIRM_EMAIL;
    public static final String RESEND_VERIFICATION = IdentityPaths.RESEND_VERIFICATION;
    public static final String FORGOT_PASSWORD = IdentityPaths.FORGOT_PASSWORD;
    public static final String RESET_PASSWORD = IdentityPaths.RESET_PASSWORD;
    public static final String CHANGE_PASSWORD = IdentityPaths.CHANGE_PASSWORD;

    /** Parametername des Tokens in Bestätigungs- und Reset-Links. */
    public static final String TOKEN_PARAMETER = IdentityPaths.TOKEN_PARAMETER;

    private IdentityRoutes() {
        // Konstanten-Klasse
    }
}
