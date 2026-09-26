package de.zettsystems.identity.ui;

import de.zettsystems.identity.values.IdentityPaths;

/**
 * The paths of the identity views in one place.
 *
 * <p>The embedding application needs them for its Spring Security configuration
 * and for links from its own menu. The values come from {@link IdentityPaths}
 * in the core, where the services build the links in the mails from them; this
 * is only the Vaadin-side access, so that the views and the embedding
 * application do not maintain a second set of constants.
 */
public final class IdentityRoutes {

    public static final String LOGIN = IdentityPaths.LOGIN;
    public static final String REGISTER = IdentityPaths.REGISTER;
    public static final String CONFIRM_EMAIL = IdentityPaths.CONFIRM_EMAIL;
    public static final String RESEND_VERIFICATION = IdentityPaths.RESEND_VERIFICATION;
    public static final String FORGOT_PASSWORD = IdentityPaths.FORGOT_PASSWORD;
    public static final String RESET_PASSWORD = IdentityPaths.RESET_PASSWORD;
    public static final String CHANGE_PASSWORD = IdentityPaths.CHANGE_PASSWORD;
    public static final String CLAIM_ACCOUNT = IdentityPaths.CLAIM_ACCOUNT;
    /** Manage the passkeys of the signed-in account (since 0.11.0). */
    public static final String PASSKEYS = IdentityPaths.PASSKEYS;
    /** The external providers linked to the signed-in account (since 1.2.0). */
    public static final String LINKED_ACCOUNTS = IdentityPaths.LINKED_ACCOUNTS;

    /** Name of the token parameter in verification, reset and invitation links. */
    public static final String TOKEN_PARAMETER = IdentityPaths.TOKEN_PARAMETER;

    private IdentityRoutes() {
        // Constants class
    }
}
