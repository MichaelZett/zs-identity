package de.zettsystems.identity.values;

/**
 * The kinds of mail this building block sends.
 *
 * <p>Part of {@link IdentityMail} so that an application's transport can route
 * by it: an invitation that stems from a tournament may leave through that
 * tournament's mail account, while a password reset always leaves through the
 * club's. Without the type the transport would have to guess from the subject.
 */
public enum IdentityMailType {
    /** Asks a newly registered person to confirm their address. */
    EMAIL_VERIFICATION,
    /** Carries the link for setting a new password. */
    PASSWORD_RESET,
    /** Invites someone to claim an account: confirm the address, set a first password. */
    INVITATION
}
