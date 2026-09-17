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

    /** Name of the token parameter in verification, reset and invitation links. */
    public static final String TOKEN_PARAMETER = "token";

    private IdentityPaths() {
        // Constants class
    }
}
