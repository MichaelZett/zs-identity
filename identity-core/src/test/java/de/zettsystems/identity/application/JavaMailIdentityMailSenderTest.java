package de.zettsystems.identity.application;

import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.NameMode;
import de.zettsystems.identity.values.ScopedRole;
import de.zettsystems.identity.values.UiSettings;
import de.zettsystems.identity.values.UserAccountDto;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.jspecify.annotations.Nullable;
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
     * A test double of our own rather than Mockito: the test wants to see the
     * message that was actually built, not merely check that a method was
     * called.
     */
    private static class CapturingMailSender implements JavaMailSender {

        private final List<MimeMessage> sent = new ArrayList<>();

        // Spring 7 declares only the varargs form in MailSender.
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
     * The actual reason for the MIME message: Outlook no longer turns a long
     * link in a plain-text mail into a clickable one.
     */
    @Test
    void theVerificationMailAlsoCarriesTheLinkAsAnHtmlAnchor() throws Exception {
        CapturingMailSender mailSender = new CapturingMailSender();
        IdentityMailSender testee = new JavaMailIdentityMailSender(mailSender, IdentityProperties.defaults(), MESSAGES);

        testee.sendEmailVerification(USER, "http://example.com/register/confirm?token=abc");

        MimeMessage message = mailSender.sent.getFirst();
        // JavaMail writes the header only when sending; there is no real
        // delivery here, so it is done by hand.
        message.saveChanges();
        assertThat(message.getContentType()).contains("multipart/");
        assertThat(htmlPart(message))
                .contains("<a href=\"http://example.com/register/confirm?token=abc\">")
                // The address also appears in plain text, for anything that
                // does not open the link.
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

    /** A display name must not break out of the markup of the HTML part. */
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
                .as("otherwise an unreachable SMTP server rolls back the whole "
                        + "registration although the account was created correctly")
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

    /**
     * The reason the language sits on the account since V1_4: the setting of
     * the application says "German", the account registered in English -- and
     * the mail is created without a browser that could be asked.
     */
    @Test
    void theAccountLanguageBeatsTheApplicationLanguage() throws Exception {
        CapturingMailSender mailSender = new CapturingMailSender();
        IdentityMailSender testee = new JavaMailIdentityMailSender(mailSender,
                IdentityProperties.defaults(), MESSAGES);

        testee.sendEmailVerification(userSpeaking(Locale.ENGLISH), "http://example.com/x");

        assertThat(mailSender.sent.getFirst().getSubject()).isEqualTo("Please confirm your e-mail address");
    }

    @Test
    void withoutALanguageAtTheAccountTheApplicationLanguageStands() throws Exception {
        CapturingMailSender mailSender = new CapturingMailSender();
        IdentityMailSender testee = new JavaMailIdentityMailSender(mailSender,
                IdentityProperties.defaults(), MESSAGES);

        testee.sendEmailVerification(userSpeaking(null), "http://example.com/x");

        assertThat(mailSender.sent.getFirst().getSubject()).contains("bestätige");
    }

    private static UserAccountDto userSpeaking(@Nullable Locale locale) {
        return new UserAccountDto(1L, "anna@example.com", AccountName.of("Anna", "Beispiel"), true, true,
                Instant.parse("2026-09-01T10:00:00Z"), Set.of(ScopedRole.global("USER")), false, locale);
    }

    private static List<String> recipients(MimeMessage message) throws Exception {
        return List.of(message.getAllRecipients()).stream().map(Object::toString).toList();
    }

    /**
     * Spring nests the text and HTML parts into a {@code multipart/alternative},
     * where part 0 is the text and part 1 the HTML. Depending on the mode of the
     * {@code MimeMessageHelper} the whole thing sits inside a
     * {@code multipart/mixed} as well, hence the search for the alternative
     * part.
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
                Locale.GERMAN, UiSettings.defaults());
    }

    private static IdentityProperties englishProperties() {
        return new IdentityProperties(true, true, Duration.ofHours(24), Duration.ofDays(7), 12,
                "noreply@localhost", "Terminplanung", "http://localhost:8080", "USER", NameMode.FULL_NAME,
                Locale.ENGLISH, UiSettings.defaults());
    }
}
