package de.zettsystems.identity.application;

import de.zettsystems.identity.values.UserAccountDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback for when the application has set up no mail delivery at all.
 *
 * <p>Writes the address together with the link to the log instead of letting
 * startup fail. Two reasons: a missing {@code JavaMailSender} would otherwise
 * abort with a hard-to-read bean message, and during development a link in the
 * log is often exactly what is wanted.
 *
 * <p>It is nothing for production, and the WARN line at startup says so
 * plainly.
 */
class LoggingIdentityMailSender implements IdentityMailSender {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingIdentityMailSender.class);

    LoggingIdentityMailSender() {
        LOG.warn("No JavaMailSender and no custom IdentityMailSender found - "
                + "verification and password reset links will only be written to the log. "
                + "Configure spring.mail.* or provide an IdentityMailSender bean.");
    }

    @Override
    public void sendEmailVerification(UserAccountDto user, String confirmationUrl) {
        LOG.warn("Email verification link for {}: {}", user.email(), confirmationUrl);
    }

    @Override
    public void sendPasswordReset(UserAccountDto user, String resetUrl) {
        LOG.warn("Password reset link for {}: {}", user.email(), resetUrl);
    }

    @Override
    public void sendInvitation(UserAccountDto user, String invitationUrl) {
        LOG.warn("Invitation link for {}: {}", user.email(), invitationUrl);
    }
}
