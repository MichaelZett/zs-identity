package de.zettsystems.identity.values;

/**
 * The paths of the identity views, relative to the context path and without a
 * leading slash.
 *
 * <p>They live in the core because the services build the links in the mails
 * from them, and because a REST application without {@code identity-vaadin}
 * needs the paths for its security configuration too. The Vaadin views point
 * at the same constants through {@code IdentityRoutes}, so that changing a
 * path stays a single edit.
 */
public final class IdentityPaths {

    public static final String LOGIN = "login";
    public static final String REGISTER = "register";
    public static final String CONFIRM_EMAIL = "register/confirm";
    public static final String RESEND_VERIFICATION = "register/resend";
    public static final String FORGOT_PASSWORD = "password/forgot";
    public static final String RESET_PASSWORD = "password/reset";
    /** Change the password while signed in; the target of a forced change. */
    public static final String CHANGE_PASSWORD = "password/change";

    /** Redeem an invitation: claim the account and set a first password. */
    public static final String CLAIM_ACCOUNT = "invitation";

    /** Manage the passkeys of the signed-in account (since 0.11.0). */
    public static final String PASSKEYS = "passkeys";

    /** Name of the token parameter in verification, reset and invitation links. */
    public static final String TOKEN_PARAMETER = "token";

    /*
     * The endpoints of the passkey sign-in, WITH a leading slash: these are
     * not views but the paths Spring Security's WebAuthn filters listen on,
     * and its request matchers want them absolute. The building block's
     * security configurer opens them; the sign-in page and the management
     * view call them from the browser.
     */

    /** {@code POST}: the challenge for a sign-in; open to everyone. */
    public static final String PASSKEY_AUTHENTICATION_OPTIONS = "/webauthn/authenticate/options";
    /** {@code POST}: the signed challenge; open to everyone, this is the sign-in itself. */
    public static final String PASSKEY_LOGIN = "/login/webauthn";
    /** {@code POST}: the challenge for registering a passkey; signed in with a password. */
    public static final String PASSKEY_REGISTRATION_OPTIONS = "/webauthn/register/options";
    /** {@code POST}: the new passkey; {@code DELETE /webauthn/register/{id}} removes one. */
    public static final String PASSKEY_REGISTRATION = "/webauthn/register";

    private IdentityPaths() {
        // Constants class
    }
}
