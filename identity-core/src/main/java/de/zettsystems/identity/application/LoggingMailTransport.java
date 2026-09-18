package de.zettsystems.identity.application;

import de.zettsystems.identity.values.IdentityMail;
import de.zettsystems.identity.values.UserAccountDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback for when the application has set up no mail delivery at all.
 *
 * <p>Writes the mail to the log instead of letting startup fail. Two reasons:
 * a missing {@code JavaMailSender} would otherwise abort with a hard-to-read
 * bean message, and during development the link in the log is often exactly
 * what is wanted. The text part carries it.
 *
 * <p>It is nothing for production, and the WARN line at startup says so
 * plainly.
 */
final class LoggingMailTransport implements IdentityMailTransport {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingMailTransport.class);

    LoggingMailTransport() {
        LOG.warn("No JavaMailSender and no IdentityMailTransport found - "
                + "verification, reset and invitation mails will only be written to the log. "
                + "Configure spring.mail.* or provide an IdentityMailTransport bean.");
    }

    @Override
    public void send(IdentityMail mail, UserAccountDto user) {
        LOG.warn("{} mail for {} ({}):\n{}", mail.type(), mail.to(), mail.subject(), mail.text());
    }
}
