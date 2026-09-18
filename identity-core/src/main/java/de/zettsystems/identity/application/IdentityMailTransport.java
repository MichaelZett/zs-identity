package de.zettsystems.identity.application;

import de.zettsystems.identity.values.IdentityMail;
import de.zettsystems.identity.values.UserAccountDto;

/**
 * Delivers a mail the building block has already rendered.
 *
 * <p>The finer of the two seams around mail. {@link IdentityMailSender}
 * replaces everything -- texts and delivery; this replaces delivery alone.
 * That is the seam for an application whose mail account depends on the
 * recipient: the transport gets the account along with the mail, looks up
 * what it needs about that account in its own tables (the club, the inviting
 * tournament) and picks server and sender address accordingly. The building
 * block itself knows nothing of tenants; it only carries the account through.
 *
 * <p>The default transport writes through {@code JavaMailSender} with the
 * sender from {@code zs.identity.from-address}/{@code from-name}; without a
 * mail library or without {@code spring.mail.*} it writes to the log. A bean
 * of the application's own displaces either.
 *
 * <p><strong>Do not let a failure escape</strong> when you can help it: a
 * delivery that throws rolls back the registration or the invitation although
 * the account was created correctly. The sender catches what does escape and
 * logs it, but a transport that logs itself can say more about the cause.
 */
public interface IdentityMailTransport {

    /**
     * Delivers one mail.
     *
     * @param mail the rendered mail; {@code mail.to()} is the address to send to
     * @param user the account the mail is for, for the application's routing
     */
    void send(IdentityMail mail, UserAccountDto user);
}
