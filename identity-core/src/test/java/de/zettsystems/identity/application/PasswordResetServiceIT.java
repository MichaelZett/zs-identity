package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.AbstractIdentityIntegrationTest;
import de.zettsystems.identity.testsupport.MutableTestClock;
import de.zettsystems.identity.testsupport.RecordingMailSender;
import de.zettsystems.identity.values.IdentityMessageKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetServiceIT extends AbstractIdentityIntegrationTest {

    private static final String EMAIL = "ida@example.com";
    private static final String OLD_PASSWORD = "das-alte-passwort";
    private static final String NEW_PASSWORD = "das-neue-passwort";

    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private PasswordResetService passwordResetService;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private AuthTokenRepository tokenRepository;
    @Autowired
    private IdentityMailSender mailSender;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private Clock clock;

    private RecordingMailSender mails;
    private MutableTestClock testClock;

    @BeforeEach
    void registerConfirmedUser() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        mails = (RecordingMailSender) mailSender;
        mails.clear();
        testClock = (MutableTestClock) clock;
        testClock.reset();

        registrationService.register(EMAIL, OLD_PASSWORD, "Ida", "Beispiel");
        registrationService.confirmEmail(mails.tokenFromLastMailTo(EMAIL));
        mails.clear();
    }

    @Test
    void theMailedLinkSetsANewPassword() {
        passwordResetService.requestReset(EMAIL);
        String token = mails.tokenFromLastMailTo(EMAIL);

        passwordResetService.resetPassword(token, NEW_PASSWORD);

        String stored = userRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, stored)).isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, stored))
                .as("das alte Passwort darf danach nicht mehr passen")
                .isFalse();
    }

    @Test
    void aResetLinkWorksOnlyOnce() {
        passwordResetService.requestReset(EMAIL);
        String token = mails.tokenFromLastMailTo(EMAIL);
        passwordResetService.resetPassword(token, NEW_PASSWORD);

        assertThatThrownBy(() -> passwordResetService.resetPassword(token, "noch-ein-passwort"))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.TOKEN_EXPIRED);
    }

    @Test
    void anExpiredResetLinkIsRejected() {
        passwordResetService.requestReset(EMAIL);
        String token = mails.tokenFromLastMailTo(EMAIL);

        testClock.advanceBy(Duration.ofHours(25));

        assertThatThrownBy(() -> passwordResetService.resetPassword(token, NEW_PASSWORD))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.TOKEN_EXPIRED);
    }

    @Test
    void requestingAResetForAnUnknownAddressStaysSilent() {
        passwordResetService.requestReset("niemand@example.com");

        assertThat(mails.sentMails())
                .as("wer hier eine Fehlermeldung bekäme, könnte damit Konten aufspüren")
                .isEmpty();
    }

    @Test
    void aTooShortNewPasswordIsRejectedBeforeTheTokenIsSpent() {
        passwordResetService.requestReset(EMAIL);
        String token = mails.tokenFromLastMailTo(EMAIL);

        assertThatThrownBy(() -> passwordResetService.resetPassword(token, "kurz"))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.PASSWORD_TOO_SHORT);

        // Das Token darf durch den Fehlversuch nicht verbraucht sein.
        passwordResetService.resetPassword(token, NEW_PASSWORD);
    }

    @Test
    void theNewPasswordWorksForSigningIn() {
        passwordResetService.requestReset(EMAIL);
        passwordResetService.resetPassword(mails.tokenFromLastMailTo(EMAIL), NEW_PASSWORD);

        var details = userDetailsService.loadUserByUsername(EMAIL);

        assertThat(passwordEncoder.matches(NEW_PASSWORD, details.getPassword())).isTrue();
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_USER");
    }
}
