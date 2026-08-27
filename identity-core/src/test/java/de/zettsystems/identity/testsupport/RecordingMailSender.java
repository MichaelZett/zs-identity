package de.zettsystems.identity.testsupport;

import de.zettsystems.identity.application.IdentityMailSender;
import de.zettsystems.identity.values.UserAccountDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Merkt sich die verschickten Mails, statt sie zuzustellen.
 *
 * <p>Ein echtes Testdoppel statt eines Mockito-Mocks: Die Tests brauchen den
 * Link aus der Mail, um den Ablauf weiterzuspielen — genau so, wie es ein Mensch
 * im Posteingang täte.
 */
public class RecordingMailSender implements IdentityMailSender {

    /** Eine verschickte Mail. */
    public record SentMail(Kind kind, String email, String url) {
    }

    public enum Kind {
        EMAIL_VERIFICATION,
        PASSWORD_RESET
    }

    private final List<SentMail> sent = new ArrayList<>();

    @Override
    public void sendEmailVerification(UserAccountDto user, String confirmationUrl) {
        sent.add(new SentMail(Kind.EMAIL_VERIFICATION, user.email(), confirmationUrl));
    }

    @Override
    public void sendPasswordReset(UserAccountDto user, String resetUrl) {
        sent.add(new SentMail(Kind.PASSWORD_RESET, user.email(), resetUrl));
    }

    public List<SentMail> sentMails() {
        return List.copyOf(sent);
    }

    public Optional<SentMail> lastMailTo(String email) {
        return sent.stream()
                .filter(mail -> mail.email().equalsIgnoreCase(email))
                .reduce((first, second) -> second);
    }

    /** Zieht das Token aus der Adresse in der Mail. */
    public String tokenFromLastMailTo(String email) {
        String url = lastMailTo(email)
                .orElseThrow(() -> new AssertionError("No mail was sent to " + email))
                .url();
        int index = url.indexOf("token=");
        if (index < 0) {
            throw new AssertionError("Mail link carries no token: " + url);
        }
        return url.substring(index + "token=".length());
    }

    public void clear() {
        sent.clear();
    }
}
