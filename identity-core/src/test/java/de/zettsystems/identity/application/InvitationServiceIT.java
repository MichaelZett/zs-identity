package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.AbstractIdentityIntegrationTest;
import de.zettsystems.identity.testsupport.MutableTestClock;
import de.zettsystems.identity.testsupport.RecordingMailSender;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.UserAccountDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvitationServiceIT extends AbstractIdentityIntegrationTest {

    private static final String EMAIL = "neu@example.com";
    private static final String PASSWORD = "das-erste-passwort";

    @Autowired
    private InvitationService invitationService;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private RegistrationService registrationService;
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
    void clean() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        mails = (RecordingMailSender) mailSender;
        mails.clear();
        testClock = (MutableTestClock) clock;
        testClock.reset();
    }

    /** Der Kern der Sache: Die Kennung bleibt, damit die fachlichen Daten daran hängen bleiben. */
    @Test
    void claimingAManagedAccountKeepsItsId() {
        Long userId = userAccountService.createManagedAccount("Ida", "Beispiel").id();

        invitationService.inviteToClaim(userId, EMAIL);
        UserAccountDto claimed = invitationService.claim(mails.tokenFromLastMailTo(EMAIL), PASSWORD);

        assertThat(claimed.id()).isEqualTo(userId);
        assertThat(claimed.email()).isEqualTo(EMAIL);
        assertThat(claimed.emailVerified())
                .as("der Link ging an genau diese Adresse — eine zweite Bestätigung wäre ein Umweg")
                .isTrue();
        assertThat(claimed.enabled()).isTrue();
    }

    @Test
    void theClaimedAccountCanSignIn() {
        Long userId = userAccountService.createManagedAccount("Ida", "Beispiel").id();
        invitationService.inviteToClaim(userId, EMAIL);

        invitationService.claim(mails.tokenFromLastMailTo(EMAIL), PASSWORD);

        var details = userDetailsService.loadUserByUsername(EMAIL);
        assertThat(passwordEncoder.matches(PASSWORD, details.getPassword())).isTrue();
        assertThat(details.isEnabled()).isTrue();
    }

    /** Vor dem Einlösen fehlt das Passwort — bis dahin gibt es keinen Anmeldeweg. */
    @Test
    void anInvitedAccountCannotSignInBeforeClaiming() {
        Long userId = userAccountService.createManagedAccount("Ida", "Beispiel").id();
        invitationService.inviteToClaim(userId, EMAIL);

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(EMAIL))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void invitingANewAccountCreatesItWithoutAPassword() {
        UserAccountDto invited = invitationService.inviteNewAccount(EMAIL, AccountName.of("Ida", "Beispiel"));

        assertThat(invited.email()).isEqualTo(EMAIL);
        assertThat(invited.emailVerified()).isFalse();
        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash())
                .as("ein Startpasswort müsste jemand übermitteln — genau das soll die Einladung ersparen")
                .isNull();
        assertThat(mails.lastMailTo(EMAIL).orElseThrow().kind())
                .isEqualTo(RecordingMailSender.Kind.INVITATION);
    }

    @Test
    void anInvitationWorksOnlyOnce() {
        Long userId = userAccountService.createManagedAccount("Ida", "Beispiel").id();
        invitationService.inviteToClaim(userId, EMAIL);
        String token = mails.tokenFromLastMailTo(EMAIL);
        invitationService.claim(token, PASSWORD);

        assertThatThrownBy(() -> invitationService.claim(token, "noch-ein-passwort"))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.TOKEN_EXPIRED);
    }

    /** Sieben Tage ist die Vorgabe — nach einem Tag gilt der Link also noch. */
    @Test
    void anInvitationOutlivesTheOtherTokens() {
        Long userId = userAccountService.createManagedAccount("Ida", "Beispiel").id();
        invitationService.inviteToClaim(userId, EMAIL);
        String token = mails.tokenFromLastMailTo(EMAIL);

        testClock.advanceBy(Duration.ofHours(25));

        assertThat(invitationService.claim(token, PASSWORD).id()).isEqualTo(userId);
    }

    @Test
    void anExpiredInvitationIsRejected() {
        Long userId = userAccountService.createManagedAccount("Ida", "Beispiel").id();
        invitationService.inviteToClaim(userId, EMAIL);
        String token = mails.tokenFromLastMailTo(EMAIL);

        testClock.advanceBy(Duration.ofDays(8));

        assertThatThrownBy(() -> invitationService.claim(token, PASSWORD))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.TOKEN_EXPIRED);
    }

    /** Eine zweite Einladung entwertet die erste — sonst gäbe es zwei gültige Wege ins Konto. */
    @Test
    void resendingInvalidatesTheEarlierInvitation() {
        Long userId = userAccountService.createManagedAccount("Ida", "Beispiel").id();
        invitationService.inviteToClaim(userId, EMAIL);
        String firstToken = mails.tokenFromLastMailTo(EMAIL);

        invitationService.resendInvitation(userId);
        String secondToken = mails.tokenFromLastMailTo(EMAIL);

        assertThatThrownBy(() -> invitationService.claim(firstToken, PASSWORD))
                .isInstanceOf(IdentityException.class);
        assertThat(invitationService.claim(secondToken, PASSWORD).id()).isEqualTo(userId);
    }

    @Test
    void anAlreadyClaimedAccountCannotBeInvitedAgain() {
        registrationService.register(EMAIL, PASSWORD, "Ida", "Beispiel");
        Long userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();

        assertThatThrownBy(() -> invitationService.resendInvitation(userId))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.ACCOUNT_ALREADY_CLAIMED);
    }

    /** Sonst wäre die Einladung ein Weg, ein fremdes Konto zu übernehmen. */
    @Test
    void anAddressThatAlreadyHasAnAccountIsRejected() {
        registrationService.register(EMAIL, PASSWORD, "Ida", "Beispiel");
        Long managedId = userAccountService.createManagedAccount("Ida", "Zweitkonto").id();

        assertThatThrownBy(() -> invitationService.inviteToClaim(managedId, EMAIL))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    void aTooShortPasswordIsRejectedBeforeTheTokenIsSpent() {
        Long userId = userAccountService.createManagedAccount("Ida", "Beispiel").id();
        invitationService.inviteToClaim(userId, EMAIL);
        String token = mails.tokenFromLastMailTo(EMAIL);

        assertThatThrownBy(() -> invitationService.claim(token, "kurz"))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.PASSWORD_TOO_SHORT);

        // Das Token darf durch den Fehlversuch nicht verbraucht sein.
        assertThat(invitationService.claim(token, PASSWORD).id()).isEqualTo(userId);
    }

    @Test
    void theViewCanLookUpWhoWasInvited() {
        Long userId = userAccountService.createManagedAccount("Ida", "Beispiel").id();
        invitationService.inviteToClaim(userId, EMAIL);

        assertThat(invitationService.findInvitee(mails.tokenFromLastMailTo(EMAIL)))
                .get()
                .extracting(UserAccountDto::id)
                .isEqualTo(userId);
        assertThat(invitationService.findInvitee("kein-echtes-token")).isEmpty();
    }

    @Test
    void invitingAnUnknownAccountFails() {
        assertThatThrownBy(() -> invitationService.inviteToClaim(4711L, EMAIL))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.ACCOUNT_NOT_FOUND);
    }
}
