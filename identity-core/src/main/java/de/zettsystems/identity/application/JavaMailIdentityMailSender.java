package de.zettsystems.identity.application;

import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.UserAccountDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Voreingestellter Mailversand über {@code JavaMailSender}.
 *
 * <p>Bewusst schlichte Textmails ohne Vorlagentechnik: Der Baustein soll keine
 * Meinung darüber haben, welche Vorlagen-Bibliothek eine Anwendung nutzt. Wer
 * gestaltete Mails will, stellt eine eigene {@link IdentityMailSender}-Bean
 * bereit und verdrängt diese hier.
 *
 * <p>Die Texte kommen aus {@link IdentityMessages}, die Sprache aus
 * {@code zs.identity.locale}: Zum Zeitpunkt des Versands gibt es keinen Browser,
 * dessen Spracheinstellung man fragen könnte, und am Konto ist keine Sprache
 * hinterlegt (siehe {@code BACKLOG.md}).
 */
class JavaMailIdentityMailSender implements IdentityMailSender {

    private static final Logger LOG = LoggerFactory.getLogger(JavaMailIdentityMailSender.class);

    private static final String VERIFICATION_SUBJECT = "identity.mail.verification.subject";
    private static final String VERIFICATION_BODY = "identity.mail.verification.body";
    private static final String RESET_SUBJECT = "identity.mail.reset.subject";
    private static final String RESET_BODY = "identity.mail.reset.body";

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
        send(user, VERIFICATION_SUBJECT, VERIFICATION_BODY, confirmationUrl);
    }

    @Override
    public void sendPasswordReset(UserAccountDto user, String resetUrl) {
        send(user, RESET_SUBJECT, RESET_BODY, resetUrl);
    }

    private void send(UserAccountDto user, String subjectKey, String bodyKey, String url) {
        Locale locale = properties.locale();
        String subject = messages.get(subjectKey, locale);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("%s <%s>".formatted(properties.fromName(), properties.fromAddress()));
        // Verwaltete Konten haben keine Adresse — sie durchlaufen aber auch
        // weder Registrierung noch Passwort-Reset; hier anzukommen wäre ein
        // Programmierfehler, kein Laufzeitfall.
        message.setTo(Objects.requireNonNull(user.email(), "email"));
        message.setSubject(subject);
        message.setText(messages.get(bodyKey, locale, user.displayName(), url, humanReadableValidity(locale)));
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // Nicht weiterwerfen: Sonst rollt eine fehlgeschlagene Zustellung die
            // Registrierung zurück, obwohl das Konto korrekt angelegt wurde. Die
            // Oberfläche bietet ein erneutes Zusenden an.
            LOG.error("Could not send '{}' mail to account {}", subject, user.id(), e);
        }
    }

    private String humanReadableValidity(Locale locale) {
        Duration validity = properties.tokenValidity();
        long hours = validity.toHours();
        if (hours >= 1) {
            return messages.get("identity.mail.validity.hours", locale, hours);
        }
        return messages.get("identity.mail.validity.minutes", locale, Math.max(1, validity.toMinutes()));
    }
}
