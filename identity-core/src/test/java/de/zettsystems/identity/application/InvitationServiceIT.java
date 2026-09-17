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

    /** The heart of the matter: the id stays, so that the domain data stays attached to it. */
    @Test
    void claimingAManagedAccountKeepsItsId() {
        Long userId = userAccountService.createManagedAccount("Ida", "Beispiel").id();

        invitationService.inviteToClaim(userId, EMAIL);
        UserAccountDto claimed = invitationService.claim(mails.tokenFromLastMailTo(EMAIL), PASSWORD);

        assertThat(claimed.id()).isEqualTo(userId);
        assertThat(claimed.email()).isEqualTo(EMAIL);
        assertThat(claimed.emailVerified())
                .as("the link went to exactly this address, so a second confirmation would be a detour")
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

    /** Before redemption there is no password, and until then no way to sign in. */
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
                .as("an initial password would have to be transmitted by someone, which the invitation avoids")
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

    /** Seven days is the default, so after one day the link is still valid. */
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

    /** A second invitation voids the first; otherwise there would be two valid ways into the account. */
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

    /** Otherwise the invitation would be a way to take over somebody else's account. */
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

        // The failed attempt must not have consumed the token.
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
