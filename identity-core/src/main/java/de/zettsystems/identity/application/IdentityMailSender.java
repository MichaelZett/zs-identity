package de.zettsystems.identity.application;

import de.zettsystems.identity.values.UserAccountDto;

/**
 * Delivery of the mails this building block sends.
 *
 * <p>A port, not a fixed implementation: the default writes through
 * {@code JavaMailSender}, while an application with a delivery path of its own
 * (transactional mail service, a queue, a test double) simply provides its own
 * bean and thereby displaces the default.
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
