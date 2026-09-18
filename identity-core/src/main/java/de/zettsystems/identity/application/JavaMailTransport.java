package de.zettsystems.identity.application;

import de.zettsystems.identity.values.IdentityMail;
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

/**
 * The default delivery through {@code JavaMailSender}, with the sender from
 * {@code zs.identity.from-address}/{@code from-name}.
 *
 * <p>Every mail goes out as {@code multipart/alternative}: the text part and
 * next to it the HTML part whose link is a real {@code <a href>}. The reason
 * is Outlook -- in plain-text messages it wraps long lines and turns a link
 * longer than 76 characters into something that can no longer be clicked.
 * With its 43-character token the verification link is always longer than
 * that. Clients without HTML still see the text part.
 *
 * <p>An application that needs another sender or another server per
 * recipient brings its own {@link IdentityMailTransport}; this class is the
 * template for it.
 */
final class JavaMailTransport implements IdentityMailTransport {

    private static final Logger LOG = LoggerFactory.getLogger(JavaMailTransport.class);

    private final JavaMailSender mailSender;
    private final IdentityProperties properties;

    JavaMailTransport(JavaMailSender mailSender, IdentityProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(IdentityMail mail, UserAccountDto user) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom("%s <%s>".formatted(properties.fromName(), properties.fromAddress()));
            helper.setTo(mail.to());
            helper.setSubject(mail.subject());
            // Both versions in one call: Spring builds multipart/alternative
            // from them, with the text part first.
            helper.setText(mail.text(), mail.html());
        } catch (MessagingException e) {
            LOG.error("Could not build {} mail for account {}", mail.type(), user.id(), e);
            return;
        }

        try {
            mailSender.send(message);
        } catch (MailException e) {
            // Do not rethrow: otherwise a failed delivery rolls back the
            // registration although the account was created correctly. The UI
            // offers to send it again.
            LOG.error("Could not send {} mail to account {}", mail.type(), user.id(), e);
        }
    }
}
