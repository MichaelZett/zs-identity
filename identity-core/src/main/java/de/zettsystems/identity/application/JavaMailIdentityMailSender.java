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
 * Voreingestellter Mailversand über {@code JavaMailSender}.
 *
 * <p>Bewusst schlichte Mails ohne Vorlagentechnik: Der Baustein soll keine
 * Meinung darüber haben, welche Vorlagen-Bibliothek eine Anwendung nutzt. Wer
 * gestaltete Mails will, stellt eine eigene {@link IdentityMailSender}-Bean
 * bereit und verdrängt diese hier.
 *
 * <p>Jede Mail geht als {@code multipart/alternative} hinaus: derselbe Text
 * wie bisher und daneben ein HTML-Teil, dessen Link ein echtes
 * {@code <a href>} ist. Grund ist Outlook — in Nur-Text-Nachrichten bricht es
 * lange Zeilen um und macht aus einem über 76 Zeichen langen Link keinen
 * anklickbaren mehr. Der Bestätigungslink ist mit dem 43-stelligen Token immer
 * länger als das. Clients ohne HTML sehen weiterhin den Textteil.
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
    private static final String VERIFICATION_BODY_HTML = "identity.mail.verification.body.html";
    private static final String RESET_SUBJECT = "identity.mail.reset.subject";
    private static final String RESET_BODY = "identity.mail.reset.body";
    private static final String RESET_BODY_HTML = "identity.mail.reset.body.html";

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
        send(user, VERIFICATION_SUBJECT, VERIFICATION_BODY, VERIFICATION_BODY_HTML, confirmationUrl);
    }

    @Override
    public void sendPasswordReset(UserAccountDto user, String resetUrl) {
        send(user, RESET_SUBJECT, RESET_BODY, RESET_BODY_HTML, resetUrl);
    }

    private void send(UserAccountDto user, String subjectKey, String bodyKey, String htmlBodyKey, String url) {
        Locale locale = properties.locale();
        String subject = messages.get(subjectKey, locale);
        String validity = humanReadableValidity(locale);
        String text = messages.get(bodyKey, locale, user.displayName(), url, validity);
        // Die Platzhalter werden vor dem Einsetzen maskiert: MessageFormat
        // kennt kein HTML, und ein Anzeigename darf die Auszeichnung nicht
        // aufbrechen.
        String html = messages.get(htmlBodyKey, locale,
                escapeHtml(user.displayName()), escapeHtml(url), escapeHtml(validity));

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom("%s <%s>".formatted(properties.fromName(), properties.fromAddress()));
            // Verwaltete Konten haben keine Adresse — sie durchlaufen aber auch
            // weder Registrierung noch Passwort-Reset; hier anzukommen wäre ein
            // Programmierfehler, kein Laufzeitfall.
            helper.setTo(Objects.requireNonNull(user.email(), "email"));
            helper.setSubject(subject);
            // Beide Fassungen in einem Aufruf: Spring baut daraus
            // multipart/alternative mit dem Text zuerst.
            helper.setText(text, html);
        } catch (MessagingException e) {
            LOG.error("Could not build '{}' mail for account {}", subject, user.id(), e);
            return;
        }

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

    /**
     * Maskiert die fünf Zeichen, die im Textinhalt und in einem doppelt
     * gequoteten Attribut Bedeutung tragen. Eine Bibliothek dafür würde dem
     * Baustein eine Abhängigkeit aufzwingen, die er sonst nicht braucht.
     */
    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
