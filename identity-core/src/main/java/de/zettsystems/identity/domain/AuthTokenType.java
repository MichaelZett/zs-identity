package de.zettsystems.identity.domain;

/** What an {@link AuthToken} entitles its holder to do. */
public enum AuthTokenType {

    /** Confirms that the given email address belongs to the account. */
    EMAIL_VERIFICATION,

    /** Allows setting a new password once. */
    PASSWORD_RESET,

    /**
     * Invites someone to claim an account: confirm the address and set a first
     * password. It lives longer than the other tokens
     * ({@code zs.identity.invitation-validity}) -- nobody reads an invitation
     * as promptly as a mail they asked for themselves.
     */
    INVITATION
}
