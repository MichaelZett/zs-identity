package de.zettsystems.identity.application;

import de.zettsystems.identity.values.UserAccountDto;

/**
 * Delivery of the mails this building block sends.
 *
 * <p>A port, not a fixed implementation: the default renders the texts of the
 * building block and hands them to an {@link IdentityMailTransport}, while an
 * application with mails of its own (a transactional mail service with its
 * own templates, a queue, a test double) simply provides its own bean and
 * thereby displaces the default.
 *
 * <p>Whoever only wants to <em>deliver</em> differently -- another server or
 * sender address per recipient -- keeps this and implements
 * {@link IdentityMailTransport} instead (since 0.9.0); the texts stay.
 */
public interface IdentityMailSender {

    /**
     * Asks for confirmation of the address.
     *
     * @param confirmationUrl complete address including the token
     */
    void sendEmailVerification(UserAccountDto user, String confirmationUrl);

    /**
     * Sends the link for resetting the password.
     *
     * @param resetUrl complete address including the token
     */
    void sendPasswordReset(UserAccountDto user, String resetUrl);

    /**
     * Invites someone to an account: confirm the address and set a first
     * password.
     *
     * <p>Deliberately a {@code default} method that fails rather than an
     * abstract one: an application's own sender (transactional mail service,
     * test double) must not stop compiling because of this addition. Silently
     * doing nothing would be worse than the error -- the invited person would
     * never get a link, and nobody would notice.
     *
     * @param invitationUrl complete address including the token
     * @throws UnsupportedOperationException as long as a custom sender does not
     *                                       override it
     */
    default void sendInvitation(UserAccountDto user, String invitationUrl) {
        throw new UnsupportedOperationException(
                getClass().getName() + " does not implement sendInvitation(..) - "
                        + "implement it to use InvitationService");
    }
}
