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

    /**
     * The identities at external providers linked to the signed-in account
     * (since 1.2.0).
     */
    public static final String LINKED_ACCOUNTS = "linked-accounts";

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

    /*
     * The endpoints of the sign-in through external providers (since 1.2.0),
     * WITH a leading slash for the same reason: Spring Security's OAuth2
     * filters listen on them. The building block's security configurer opens
     * them.
     */

    /**
     * {@code GET /oauth2/authorization/{registrationId}}: sends the browser to
     * the provider. Open to everyone.
     */
    public static final String OAUTH2_AUTHORIZATION = "/oauth2/authorization";
    /** {@code GET}: where the provider sends the browser back; open to everyone. */
    public static final String OAUTH2_CALLBACK = "/login/oauth2/code/*";

    /**
     * Parameter on {@link #OAUTH2_AUTHORIZATION}, a flag without value: redeem
     * the invitation the redemption view left in the session with the
     * provider's identity, instead of setting a password. The token itself
     * never travels in the address (see
     * {@code ExternalSignInService#PENDING_INVITATION_SESSION_ATTRIBUTE}).
     */
    public static final String INVITATION_PARAMETER = "invitation";
    /**
     * Parameter on {@link #OAUTH2_AUTHORIZATION}: link the provider's identity
     * to the signed-in account instead of signing in. Needs a fresh sign-in.
     */
    public static final String LINK_PARAMETER = "link";

    /**
     * Parameter the sign-in page receives, next to {@code error}, when a
     * sign-in through a provider was refused: the reason, as the lower-case
     * name of an {@code ExternalSignInException.Reason}. The linked-accounts
     * view receives it without {@code error} when linking failed.
     */
    public static final String EXTERNAL_ERROR_PARAMETER = "external";

    /**
     * Parameter the linked-accounts view receives after a provider was linked:
     * its registration id.
     */
    public static final String LINKED_PARAMETER = "linked";

    private IdentityPaths() {
        // Constants class
    }
}
