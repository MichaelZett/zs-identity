package de.zettsystems.identity.application;

import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.IdentityMail;
import de.zettsystems.identity.values.IdentityMailType;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.UserAccountDto;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The seam of 0.9.0: the building block renders, the application's transport
 * delivers. What the transport gets handed is pinned down here -- it is what an
 * application builds its routing on.
 */
class IdentityMailTransportTest {

    private static final IdentityMessages MESSAGES = IdentityMessages.resourceBundles();
    private static final UserAccountDto USER = new UserAccountDto(7L, "anna@example.com",
            AccountName.of("Anna", "Beispiel"), true, true, Instant.parse("2026-09-01T10:00:00Z"), Set.of("USER"));

    private final RecordingTransport transport = new RecordingTransport();
    private final IdentityMailSender testee =
            IdentityMailFactory.viaTransport(transport, IdentityProperties.defaults(), MESSAGES);

    @Test
    void everyKindOfMailArrivesAtTheTransportWithItsType() {
        testee.sendEmailVerification(USER, "http://example.com/confirm?token=a");
        testee.sendPasswordReset(USER, "http://example.com/reset?token=b");
        testee.sendInvitation(USER, "http://example.com/claim?token=c");

        assertThat(transport.mails)
                .extracting(IdentityMail::type)
                .containsExactly(IdentityMailType.EMAIL_VERIFICATION, IdentityMailType.PASSWORD_RESET,
                        IdentityMailType.INVITATION);
        assertThat(transport.users).allSatisfy(user -> assertThat(user).isSameAs(USER));
    }

    /** The application does not touch the texts -- so they have to be complete on arrival. */
    @Test
    void theMailIsRenderedCompletelyBeforeItReachesTheTransport() {
        testee.sendInvitation(USER, "http://example.com/claim?token=c&x=<y>");

        IdentityMail mail = transport.mails.getFirst();
        assertThat(mail.to()).isEqualTo("anna@example.com");
        assertThat(mail.subject()).isNotBlank();
        assertThat(mail.text()).contains("Anna").contains("http://example.com/claim?token=c&x=<y>");
        assertThat(mail.html())
                .as("the HTML part escapes what the text part carries verbatim")
                .contains("href=\"http://example.com/claim?token=c&amp;x=&lt;y&gt;\"");
        assertThat(mail.text()).as("the invitation names its longer validity").contains("7");
    }

    @Test
    void theLockNoticeNamesTheDurationAndLeadsToForgotPassword() {
        testee.sendAccountTemporarilyLocked(USER, "http://example.com/password/forgot", Duration.ofMinutes(30));

        IdentityMail mail = transport.mails.getFirst();
        assertThat(mail.type()).isEqualTo(IdentityMailType.ACCOUNT_TEMPORARILY_LOCKED);
        assertThat(mail.subject()).isEqualTo("Dein Zugang ist vorübergehend gesperrt");
        assertThat(mail.text()).contains("Anna").contains("30 Minuten").contains("http://example.com/password/forgot");
        assertThat(mail.html()).contains("href=\"http://example.com/password/forgot\"");
    }

    /** An application's own sender predating 1.1.0 keeps compiling and simply sends no notice. */
    @Test
    void aSenderOfItsOwnSendsNoLockNoticeUnlessItWantsTo() {
        IdentityMailSender own = new IdentityMailSender() {
            @Override
            public void sendEmailVerification(UserAccountDto user, String confirmationUrl) {
                throw new AssertionError("not called");
            }

            @Override
            public void sendPasswordReset(UserAccountDto user, String resetUrl) {
                throw new AssertionError("not called");
            }
        };

        assertThatCode(() -> own.sendAccountTemporarilyLocked(USER, "http://example.com/x", Duration.ofMinutes(15)))
                .doesNotThrowAnyException();
    }

    @Test
    void theLanguageOfTheAccountDecides() {
        UserAccountDto english = new UserAccountDto(USER.id(), USER.email(), USER.name(), true, true,
                USER.createdAt(), USER.roleAssignments(), false, java.util.Locale.ENGLISH);

        testee.sendPasswordReset(english, "http://example.com/reset");

        assertThat(transport.mails.getFirst().subject()).isEqualTo("Reset your password");
    }

    /** The registration must not roll back because a mail server was down. */
    @Test
    void aTransportThatThrowsDoesNotTakeTheCallerDown() {
        IdentityMailSender failing = IdentityMailFactory.viaTransport((mail, user) -> {
            throw new IllegalStateException("relay refused");
        }, IdentityProperties.defaults(), MESSAGES);

        assertThatCode(() -> failing.sendEmailVerification(USER, "http://example.com/x"))
                .doesNotThrowAnyException();
    }

    private static final class RecordingTransport implements IdentityMailTransport {
        private final List<IdentityMail> mails = new ArrayList<>();
        private final List<UserAccountDto> users = new ArrayList<>();

        @Override
        public void send(IdentityMail mail, UserAccountDto user) {
            mails.add(mail);
            users.add(user);
        }
    }
}
