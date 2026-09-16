package de.zettsystems.identity.application;

import de.zettsystems.identity.values.UserAccountDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rückfallebene, wenn die Anwendung gar keinen Mailversand eingerichtet hat.
 *
 * <p>Schreibt die Adresse samt Link ins Log, statt den Start scheitern zu lassen.
 * Zwei Gründe: Ein fehlender {@code JavaMailSender} würde sonst mit einer
 * schwer deutbaren Bean-Meldung abbrechen, und in der Entwicklung ist ein Link
 * im Log oft genau das, was man braucht.
 *
 * <p>Für den Produktivbetrieb ist das nichts — die WARN-Zeile beim Start sagt
 * das auch deutlich.
 */
class LoggingIdentityMailSender implements IdentityMailSender {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingIdentityMailSender.class);

    LoggingIdentityMailSender() {
        LOG.warn("No JavaMailSender and no custom IdentityMailSender found — "
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
