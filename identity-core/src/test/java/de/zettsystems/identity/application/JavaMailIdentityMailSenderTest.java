package de.zettsystems.identity.application;

import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.NameMode;
import de.zettsystems.identity.values.UserAccountDto;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class JavaMailIdentityMailSenderTest {

    private static final IdentityMessages MESSAGES = IdentityMessages.resourceBundles();

    private static final UserAccountDto USER = new UserAccountDto(1L, "anna@example.com",
            AccountName.of("Anna", "Beispiel"), true, true, Instant.parse("2026-09-01T10:00:00Z"), Set.of("USER"));

    /**
     * Eigenes Testdoppel statt Mockito: Der Test will die tatsächlich gebaute
     * Nachricht sehen, nicht nur prüfen, dass eine Methode aufgerufen wurde.
     */
    private static class CapturingMailSender implements JavaMailSender {

        private final List<MimeMessage> sent = new ArrayList<>();

        // Spring 7 deklariert in MailSender nur noch die Varargs-Form.
        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            throw new UnsupportedOperationException("die Mails gehen als MIME-Nachricht hinaus");
        }

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getInstance(new Properties()));
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            throw new UnsupportedOperationException("not needed for these tests");
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            sent.addAll(List.of(mimeMessages));
        }
    }

    private static final class FailingMailSender extends CapturingMailSender {
        @Override
        public void send(MimeMessage... mimeMessages) {
            throw new MailSendException("SMTP server unreachable");
        }
    }

    @Test
    void theVerificationMailCarriesRecipientSubjectAndLink() throws Exception {
        CapturingMailSender mailSender = new CapturingMailSender();
        IdentityMailSender testee = new JavaMailIdentityMailSender(mailSender, IdentityProperties.defaults(), MESSAGES);

        testee.sendEmailVerification(USER, "http://example.com/register/confirm?token=abc");

        assertThat(mailSender.sent).hasSize(1);
        MimeMessage message = mailSender.sent.getFirst();
        assertThat(recipients(message)).containsExactly("anna@example.com");
        assertThat(message.getSubject()).contains("bestätige");
        assertThat(plainPart(message))
                .contains("Anna")
                .contains("http://example.com/register/confirm?token=abc");
        assertThat(message.getFrom()[0].toString()).contains("noreply@localhost");
    }

    /**
     * Der eigentliche Grund für die MIME-Nachricht: Outlook macht aus einem
     * langen Link in einer Nur-Text-Mail keinen anklickbaren mehr.
     */
    @Test
    void theVerificationMailAlsoCarriesTheLinkAsAnHtmlAnchor() throws Exception {
        CapturingMailSender mailSender = new CapturingMailSender();
        IdentityMailSender testee = new JavaMailIdentityMailSender(mailSender, IdentityProperties.defaults(), MESSAGES);

        testee.sendEmailVerification(USER, "http://example.com/register/confirm?token=abc");

        MimeMessage message = mailSender.sent.getFirst();
        // Den Kopf schreibt JavaMail erst beim Absenden; hier gibt es keinen
        // echten Versand, also von Hand.
        message.saveChanges();
        assertThat(message.getContentType()).contains("multipart/");
        assertThat(htmlPart(message))
                .contains("<a href=\"http://example.com/register/confirm?token=abc\">")
                // Die Adresse steht zusätzlich im Klartext, für alles, was den
                // Verweis nicht öffnet.
                .contains("<br>http://example.com/register/confirm?token=abc");
    }

    @Test
    void theResetMailCarriesTheResetLink() throws Exception {
        CapturingMailSender mailSender = new CapturingMailSender();
        IdentityMailSender testee = new JavaMailIdentityMailSender(mailSender, IdentityProperties.defaults(), MESSAGES);

        testee.sendPasswordReset(USER, "http://example.com/password/reset?token=xyz");

        MimeMessage message = mailSender.sent.getFirst();
        assertThat(message.getSubject()).contains("Passwort");
        assertThat(plainPart(message)).contains("http://example.com/password/reset?token=xyz");
        assertThat(htmlPart(message)).contains("<a href=\"http://example.com/password/reset?token=xyz\">");
    }

    /** Ein Anzeigename darf die Auszeichnung des HTML-Teils nicht aufbrechen. */
    @Test
    void aDisplayNameWithMarkupIsEscapedInTheHtmlPart() throws Exception {
        CapturingMailSender mailSender = new CapturingMailSender();
        IdentityMailSender testee = new JavaMailIdentityMailSender(mailSender, IdentityProperties.defaults(), MESSAGES);
        UserAccountDto tricky = new UserAccountDto(2L, "b@example.com",
                AccountName.display("<script>alert(1)</script>"), true, true,
                Instant.parse("2026-09-01T10:00:00Z"), Set.of("USER"));

        testee.sendEmailVerification(tricky, "http://example.com/x");

        assertThat(htmlPart(mailSender.sent.getFirst()))
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
    }

    @Test
    void theValidityPeriodIsSpelledOutInTheMail() throws Exception {
        CapturingMailSender mailSender = new CapturingMailSender();
        IdentityMailSender testee = new JavaMailIdentityMailSender(mailSender,
                propertiesWithValidity(Duration.ofHours(2)), MESSAGES);

        testee.sendEmailVerification(USER, "http://example.com/x");

        assertThat(plainPart(mailSender.sent.getFirst())).contains("2 Stunden");
    }

    @Test
    void aValidityBelowAnHourIsSpelledOutInMinutes() throws Exception {
        CapturingMailSender mailSender = new CapturingMailSender();
        IdentityMailSender testee = new JavaMailIdentityMailSender(mailSender,
                propertiesWithValidity(Duration.ofMinutes(30)), MESSAGES);

        testee.sendEmailVerification(USER, "http://example.com/x");

        assertThat(plainPart(mailSender.sent.getFirst())).contains("30 Minuten");
    }

    @Test
    void aFailedDeliveryDoesNotPropagate() {
        IdentityMailSender testee = new JavaMailIdentityMailSender(new FailingMailSender(),
                IdentityProperties.defaults(), MESSAGES);

        assertThatCode(() -> testee.sendEmailVerification(USER, "http://example.com/x"))
                .as("sonst rollt ein unerreichbarer SMTP-Server die gesamte Registrierung "
                        + "zurück, obwohl das Konto korrekt angelegt wurde")
                .doesNotThrowAnyException();
    }

    @Test
    void theMailFollowsTheConfiguredLocale() throws Exception {
        CapturingMailSender mailSender = new CapturingMailSender();
        IdentityMailSender testee = new JavaMailIdentityMailSender(mailSender, englishProperties(), MESSAGES);

        testee.sendEmailVerification(USER, "http://example.com/x");

        MimeMessage message = mailSender.sent.getFirst();
        assertThat(message.getSubject()).isEqualTo("Please confirm your e-mail address");
        assertThat(plainPart(message)).contains("The link is valid for 24 hours");
        assertThat(htmlPart(message)).contains("Confirm e-mail address");
    }

    private static List<String> recipients(MimeMessage message) throws Exception {
        return List.of(message.getAllRecipients()).stream().map(Object::toString).toList();
    }

    /**
     * Spring schachtelt Text- und HTML-Teil in ein {@code multipart/alternative}
     * — Teil 0 ist der Text, Teil 1 das HTML. Je nach Modus des
     * {@code MimeMessageHelper} steckt das Ganze noch in einem
     * {@code multipart/mixed}, deshalb die Suche nach dem alternativen Teil.
     */
    private static String plainPart(MimeMessage message) throws Exception {
        return partContent(message, 0);
    }

    private static String htmlPart(MimeMessage message) throws Exception {
        return partContent(message, 1);
    }

    private static String partContent(MimeMessage message, int index) throws Exception {
        MimeMultipart alternative = alternativePart((MimeMultipart) message.getContent());
        return alternative.getBodyPart(index).getContent().toString();
    }

    private static MimeMultipart alternativePart(MimeMultipart multipart) throws Exception {
        if (multipart.getContentType().contains("alternative")) {
            return multipart;
        }
        return alternativePart((MimeMultipart) multipart.getBodyPart(0).getContent());
    }

    private static IdentityProperties propertiesWithValidity(Duration validity) {
        return new IdentityProperties(true, true, validity, validity, 12,
                "noreply@localhost", "Terminplanung", "http://localhost:8080", "USER", NameMode.FULL_NAME,
                Locale.GERMAN);
    }

    private static IdentityProperties englishProperties() {
        return new IdentityProperties(true, true, Duration.ofHours(24), Duration.ofDays(7), 12,
                "noreply@localhost", "Terminplanung", "http://localhost:8080", "USER", NameMode.FULL_NAME,
                Locale.ENGLISH);
    }
}
