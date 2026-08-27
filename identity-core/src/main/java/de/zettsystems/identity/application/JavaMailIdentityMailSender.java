package de.zettsystems.identity.application;

import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.UserAccountDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.util.Objects;

/**
 * Voreingestellter Mailversand über {@code JavaMailSender}.
 *
 * <p>Bewusst schlichte Textmails ohne Vorlagentechnik: Der Baustein soll keine
 * Meinung darüber haben, welche Vorlagen-Bibliothek eine Anwendung nutzt. Wer
 * gestaltete Mails will, stellt eine eigene {@link IdentityMailSender}-Bean
 * bereit und verdrängt diese hier.
 */
class JavaMailIdentityMailSender implements IdentityMailSender {

    private static final Logger LOG = LoggerFactory.getLogger(JavaMailIdentityMailSender.class);

    private final JavaMailSender mailSender;
    private final IdentityProperties properties;

    JavaMailIdentityMailSender(JavaMailSender mailSender, IdentityProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendEmailVerification(UserAccountDto user, String confirmationUrl) {
        send(user, "Bitte bestätige deine E-Mail-Adresse", """
                Hallo %s,

                bitte bestätige deine E-Mail-Adresse über diesen Link:

                %s

                Der Link ist %s gültig. Wenn du dich nicht registriert hast,
                kannst du diese Nachricht ignorieren.
                """.formatted(user.displayName(), confirmationUrl, humanReadableValidity()));
    }

    @Override
    public void sendPasswordReset(UserAccountDto user, String resetUrl) {
        send(user, "Passwort zurücksetzen", """
                Hallo %s,

                über diesen Link kannst du ein neues Passwort setzen:

                %s

                Der Link ist %s gültig. Wenn du kein neues Passwort angefordert
                hast, ignoriere diese Nachricht — dein aktuelles Passwort bleibt
                unverändert.
                """.formatted(user.displayName(), resetUrl, humanReadableValidity()));
    }

    private void send(UserAccountDto user, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("%s <%s>".formatted(properties.fromName(), properties.fromAddress()));
        // Verwaltete Konten haben keine Adresse — sie durchlaufen aber auch
        // weder Registrierung noch Passwort-Reset; hier anzukommen wäre ein
        // Programmierfehler, kein Laufzeitfall.
        message.setTo(Objects.requireNonNull(user.email(), "email"));
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // Nicht weiterwerfen: Sonst rollt eine fehlgeschlagene Zustellung die
            // Registrierung zurück, obwohl das Konto korrekt angelegt wurde. Die
            // Oberfläche bietet ein erneutes Zusenden an.
            LOG.error("Could not send '{}' mail to account {}", subject, user.id(), e);
        }
    }

    private String humanReadableValidity() {
        Duration validity = properties.tokenValidity();
        long hours = validity.toHours();
        if (hours >= 1) {
            return hours == 1 ? "eine Stunde" : hours + " Stunden";
        }
        long minutes = Math.max(1, validity.toMinutes());
        return minutes == 1 ? "eine Minute" : minutes + " Minuten";
    }
}
