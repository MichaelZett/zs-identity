package de.zettsystems.identity.application;

import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.UserAccountDto;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * The default mail delivery through {@code JavaMailSender}.
 *
 * <p>Deliberately plain mails without a templating engine: the building block
 * should hold no opinion about which template library an application uses.
 * Whoever wants designed mails provides an {@link IdentityMailSender} bean of
 * their own and displaces this one.
 *
 * <p>Every mail goes out as {@code multipart/alternative}: the same text as
 * before, and next to it an HTML part whose link is a real {@code <a href>}.
 * The reason is Outlook -- in plain-text messages it wraps long lines and
 * turns a link longer than 76 characters into something that can no longer be
 * clicked. With its 43-character token the verification link is always longer
 * than that. Clients without HTML still see the text part.
 *
 * <p>The texts come from {@link IdentityMessages}. The language is that of the
 * account, or {@code zs.identity.locale} if it has chosen none: at delivery
 * time there is no browser whose language setting could be asked, which is why
 * it sits on the account since V1_4.
 */
class JavaMailIdentityMailSender implements IdentityMailSender {

    private static final Logger LOG = LoggerFactory.getLogger(JavaMailIdentityMailSender.class);

    private static final String VERIFICATION_SUBJECT = "identity.mail.verification.subject";
    private static final String VERIFICATION_BODY = "identity.mail.verification.body";
    private static final String VERIFICATION_BODY_HTML = "identity.mail.verification.body.html";
    private static final String RESET_SUBJECT = "identity.mail.reset.subject";
    private static final String RESET_BODY = "identity.mail.reset.body";
    private static final String RESET_BODY_HTML = "identity.mail.reset.body.html";
    private static final String INVITATION_SUBJECT = "identity.mail.invitation.subject";
    private static final String INVITATION_BODY = "identity.mail.invitation.body";
    private static final String INVITATION_BODY_HTML = "identity.mail.invitation.body.html";

    private final JavaMailSender mailSender;
    private final IdentityProperties properties;
    private final IdentityMessages messages;

    JavaMailIdentityMailSender(JavaMailSender mailSender, IdentityProperties properties, IdentityMessages messages) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.messages = messages;
    }

    @Override
    public void sendEmailVerification(UserAccountDto user, String confirmationUrl) {
        send(user, VERIFICATION_SUBJECT, VERIFICATION_BODY, VERIFICATION_BODY_HTML, confirmationUrl,
                properties.tokenValidity());
    }

    @Override
    public void sendPasswordReset(UserAccountDto user, String resetUrl) {
        send(user, RESET_SUBJECT, RESET_BODY, RESET_BODY_HTML, resetUrl, properties.tokenValidity());
    }

    @Override
    public void sendInvitation(UserAccountDto user, String invitationUrl) {
        send(user, INVITATION_SUBJECT, INVITATION_BODY, INVITATION_BODY_HTML, invitationUrl,
                properties.invitationValidity());
    }

    private void send(UserAccountDto user, String subjectKey, String bodyKey, String htmlBodyKey, String url,
                      Duration linkValidity) {
        Locale locale = user.localeOr(properties.locale());
        String subject = messages.get(subjectKey, locale);
        String validity = humanReadableValidity(locale, linkValidity);
        String text = messages.get(bodyKey, locale, user.displayName(), url, validity);
        // The placeholders are escaped before being inserted: MessageFormat
        // knows no HTML, and a display name must not break out of the markup.
        String html = messages.get(htmlBodyKey, locale,
                escapeHtml(user.displayName()), escapeHtml(url), escapeHtml(validity));

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom("%s <%s>".formatted(properties.fromName(), properties.fromAddress()));
            // Managed accounts have no address, but they go through neither
            // registration nor password reset; arriving here would be a
            // programming error, not a runtime case.
            helper.setTo(Objects.requireNonNull(user.email(), "email"));
            helper.setSubject(subject);
            // Both versions in one call: Spring builds multipart/alternative
            // from them, with the text part first.
            helper.setText(text, html);
        } catch (MessagingException e) {
            LOG.error("Could not build '{}' mail for account {}", subject, user.id(), e);
            return;
        }

        try {
            mailSender.send(message);
        } catch (MailException e) {
            // Do not rethrow: otherwise a failed delivery rolls back the
            // registration although the account was created correctly. The UI
            // offers to send it again.
            LOG.error("Could not send '{}' mail to account {}", subject, user.id(), e);
        }
    }

    /**
     * The deadline in the largest unit that comes out even: writing a
     * seven-day invitation as "168 hours" would be correct and unreadable.
     *
     * <p>Only <strong>above</strong> a day, and only for whole days: the
     * existing deadline of 24 hours should keep reading "24 hours" in the
     * existing mails, and "25 hours" is more honest than "one day".
     */
    private String humanReadableValidity(Locale locale, Duration validity) {
        long hours = validity.toHours();
        if (hours > 24 && validity.toHoursPart() == 0 && validity.toMinutesPart() == 0) {
            return messages.get("identity.mail.validity.days", locale, validity.toDays());
        }
        if (hours >= 1) {
            return messages.get("identity.mail.validity.hours", locale, hours);
        }
        return messages.get("identity.mail.validity.minutes", locale, Math.max(1, validity.toMinutes()));
    }

    /**
     * Escapes the five characters that carry meaning in text content and
     * inside a double-quoted attribute. A library for this would force a
     * dependency on the building block that it otherwise does not need.
     */
    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
