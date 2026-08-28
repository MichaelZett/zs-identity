package de.zettsystems.identity.application;

import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.NameMode;
import de.zettsystems.identity.values.UserAccountDto;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

        private final List<SimpleMailMessage> sent = new ArrayList<>();

        // Spring 7 deklariert in MailSender nur noch die Varargs-Form.
        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            sent.addAll(List.of(simpleMessages));
        }

        @Override
        public jakarta.mail.internet.MimeMessage createMimeMessage() {
            throw new UnsupportedOperationException("not needed for these tests");
        }

        @Override
        public jakarta.mail.internet.MimeMessage createMimeMessage(java.io.InputStream contentStream) {
            throw new UnsupportedOperationException("not needed for these tests");
        }

        @Override
        public void send(jakarta.mail.internet.MimeMessage... mimeMessages) {
            throw new UnsupportedOperationException("not needed for these tests");
        }
    }

    private static final class FailingMailSender extends CapturingMailSender {
        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            throw new MailSendException("SMTP server unreachable");
        }
    }

    @Test
    void theVerificationMailCarriesRecipientSubjectAndLink() {
        CapturingMailSender mailSender = new CapturingMailSender();
        IdentityMailSender testee = new JavaMailIdentityMailSender(mailSender, IdentityProperties.defaults(), MESSAGES);

        testee.sendEmailVerification(USER, "http://example.com/register/confirm?token=abc");

        assertThat(mailSender.sent).hasSize(1);
        SimpleMailMessage message = mailSender.sent.getFirst();
        assertThat(message.getTo()).containsExactly("anna@example.com");
        assertThat(message.getSubject()).contains("bestätige");
        assertThat(message.getText())
                .contains("Anna")
                .contains("http://example.com/register/confirm?token=abc");
        assertThat(message.getFrom()).contains("noreply@localhost");
    }

    @Test
    void theResetMailCarriesTheResetLink() {
        CapturingMailSender mailSender = new CapturingMailSender();
        IdentityMailSender testee = new JavaMailIdentityMailSender(mailSender, IdentityProperties.defaults(), MESSAGES);

        testee.sendPasswordReset(USER, "http://example.com/password/reset?token=xyz");

        SimpleMailMessage message = mailSender.sent.getFirst();
        assertThat(message.getSubject()).contains("Passwort");
        assertThat(message.getText()).contains("http://example.com/password/reset?token=xyz");
    }

    @Test
    void theValidityPeriodIsSpelledOutInTheMail() {
        CapturingMailSender mailSender = new CapturingMailSender();
        IdentityMailSender testee = new JavaMailIdentityMailSender(mailSender, propertiesWithValidity(Duration.ofHours(2)), MESSAGES);

        testee.sendEmailVerification(USER, "http://example.com/x");

        assertThat(mailSender.sent.getFirst().getText()).contains("2 Stunden");
    }

    @Test
    void aValidityBelowAnHourIsSpelledOutInMinutes() {
        CapturingMailSender mailSender = new CapturingMailSender();
        IdentityMailSender testee = new JavaMailIdentityMailSender(mailSender, propertiesWithValidity(Duration.ofMinutes(30)), MESSAGES);

        testee.sendEmailVerification(USER, "http://example.com/x");

        assertThat(mailSender.sent.getFirst().getText()).contains("30 Minuten");
    }

    @Test
    void aFailedDeliveryDoesNotPropagate() {
        IdentityMailSender testee = new JavaMailIdentityMailSender(new FailingMailSender(), IdentityProperties.defaults(), MESSAGES);

        assertThatCode(() -> testee.sendEmailVerification(USER, "http://example.com/x"))
                .as("sonst rollt ein unerreichbarer SMTP-Server die gesamte Registrierung "
                        + "zurück, obwohl das Konto korrekt angelegt wurde")
                .doesNotThrowAnyException();
    }

    @Test
    void theMailFollowsTheConfiguredLocale() {
        CapturingMailSender mailSender = new CapturingMailSender();
        IdentityMailSender testee = new JavaMailIdentityMailSender(mailSender, englishProperties(), MESSAGES);

        testee.sendEmailVerification(USER, "http://example.com/x");

        SimpleMailMessage message = mailSender.sent.getFirst();
        assertThat(message.getSubject()).isEqualTo("Please confirm your e-mail address");
        assertThat(message.getText()).contains("The link is valid for 24 hours");
    }

    private static IdentityProperties propertiesWithValidity(Duration validity) {
        return new IdentityProperties(true, true, validity, 12,
                "noreply@localhost", "Terminplanung", "http://localhost:8080", "USER", NameMode.FULL_NAME,
                Locale.GERMAN);
    }

    private static IdentityProperties englishProperties() {
        return new IdentityProperties(true, true, Duration.ofHours(24), 12,
                "noreply@localhost", "Terminplanung", "http://localhost:8080", "USER", NameMode.FULL_NAME,
                Locale.ENGLISH);
    }
}
