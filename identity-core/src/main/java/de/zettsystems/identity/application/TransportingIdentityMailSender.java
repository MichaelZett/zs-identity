package de.zettsystems.identity.application;

import de.zettsystems.identity.values.IdentityMail;
import de.zettsystems.identity.values.IdentityMailType;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.UserAccountDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * The default {@link IdentityMailSender}: renders through
 * {@link IdentityMailRenderer} and hands the result to an
 * {@link IdentityMailTransport}.
 *
 * <p>Whatever the transport lets escape is caught here and logged: a failed
 * delivery must not roll back the registration although the account was
 * created correctly. The UI offers to send the mail again.
 */
final class TransportingIdentityMailSender implements IdentityMailSender {

    private static final Logger LOG = LoggerFactory.getLogger(TransportingIdentityMailSender.class);

    private final IdentityMailRenderer renderer;
    private final IdentityMailTransport transport;

    TransportingIdentityMailSender(IdentityMailTransport transport, IdentityProperties properties,
                                   IdentityMessages messages) {
        this.renderer = new IdentityMailRenderer(properties, messages);
        this.transport = transport;
    }

    @Override
    public void sendEmailVerification(UserAccountDto user, String confirmationUrl) {
        send(IdentityMailType.EMAIL_VERIFICATION, user, confirmationUrl);
    }

    @Override
    public void sendPasswordReset(UserAccountDto user, String resetUrl) {
        send(IdentityMailType.PASSWORD_RESET, user, resetUrl);
    }

    @Override
    public void sendInvitation(UserAccountDto user, String invitationUrl) {
        send(IdentityMailType.INVITATION, user, invitationUrl);
    }

    @Override
    public void sendAccountTemporarilyLocked(UserAccountDto user, String forgotPasswordUrl, Duration lockDuration) {
        deliver(IdentityMailType.ACCOUNT_TEMPORARILY_LOCKED, user,
                renderer.render(IdentityMailType.ACCOUNT_TEMPORARILY_LOCKED, user, forgotPasswordUrl, lockDuration));
    }

    private void send(IdentityMailType type, UserAccountDto user, String url) {
        deliver(type, user, renderer.render(type, user, url));
    }

    private void deliver(IdentityMailType type, UserAccountDto user, IdentityMail mail) {
        try {
            transport.send(mail, user);
        } catch (RuntimeException e) {
            LOG.error("Could not send {} mail to account {}", type, user.id(), e);
        }
    }
}
