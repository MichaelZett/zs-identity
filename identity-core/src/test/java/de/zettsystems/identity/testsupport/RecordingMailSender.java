package de.zettsystems.identity.testsupport;

import de.zettsystems.identity.application.IdentityMailSender;
import de.zettsystems.identity.values.UserAccountDto;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Remembers the mails that were sent instead of delivering them.
 *
 * <p>A real test double rather than a Mockito mock: the tests need the link
 * from the mail to play the flow through, exactly as a human would from their
 * inbox.
 */
public class RecordingMailSender implements IdentityMailSender {

    /** One mail that was sent. */
    public record SentMail(Kind kind, String email, String url) {
    }

    public enum Kind {
        EMAIL_VERIFICATION,
        PASSWORD_RESET,
        INVITATION,
        ACCOUNT_TEMPORARILY_LOCKED
    }

    // Thread-safe: the lock notice is sent from a thread of its own.
    private final List<SentMail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void sendEmailVerification(UserAccountDto user, String confirmationUrl) {
        sent.add(new SentMail(Kind.EMAIL_VERIFICATION, user.email(), confirmationUrl));
    }

    @Override
    public void sendPasswordReset(UserAccountDto user, String resetUrl) {
        sent.add(new SentMail(Kind.PASSWORD_RESET, user.email(), resetUrl));
    }

    @Override
    public void sendInvitation(UserAccountDto user, String invitationUrl) {
        sent.add(new SentMail(Kind.INVITATION, user.email(), invitationUrl));
    }

    @Override
    public void sendAccountTemporarilyLocked(UserAccountDto user, String forgotPasswordUrl, Duration lockDuration) {
        sent.add(new SentMail(Kind.ACCOUNT_TEMPORARILY_LOCKED, user.email(), forgotPasswordUrl));
    }

    public List<SentMail> sentMails() {
        return List.copyOf(sent);
    }

    public Optional<SentMail> lastMailTo(String email) {
        return sent.stream()
                .filter(mail -> mail.email().equalsIgnoreCase(email))
                .reduce((first, second) -> second);
    }

    /** Pulls the token out of the address in the mail. */
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
