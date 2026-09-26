package de.zettsystems.identity.application;

import de.zettsystems.identity.application.ExternalSignInException.Reason;
import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.AuthTokenType;
import de.zettsystems.identity.domain.ExternalIdentityRepository;
import de.zettsystems.identity.domain.RoleRepository;
import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.AbstractIdentityIntegrationTest;
import de.zettsystems.identity.testsupport.MutableTestClock;
import de.zettsystems.identity.testsupport.RecordingMailSender;
import de.zettsystems.identity.testsupport.RecordingTokenRepository;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.ExternalIdentityClaims;
import de.zettsystems.identity.values.ExternalIdentityDto;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.LoginProtectionSettings;
import de.zettsystems.identity.values.OAuth2Settings;
import de.zettsystems.identity.values.UserAccountDto;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The rules of the sign-in through external providers against a real
 * database: who gets in, into which account, and what happens to the account
 * on the way.
 */
class ExternalSignInServiceIT extends AbstractIdentityIntegrationTest {

    private static final String EMAIL = "ida@example.com";
    private static final String PASSWORD = "ein-langes-passwort";

    @Autowired
    private ExternalSignInService signInService;
    @Autowired
    private ExternalIdentityService identityService;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private InvitationService invitationService;
    @Autowired
    private PasswordResetService passwordResetService;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private ExternalIdentityRepository identityRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private AuthTokenRepository tokenRepository;
    @Autowired
    private AuthTokenIssuer tokenIssuer;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private IdentityMailSender mailSender;
    @Autowired
    private PersistentTokenRepository rememberMeTokens;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ApplicationContext context;
    @Autowired
    private Clock clock;

    private RecordingMailSender mails;
    private RecordingTokenRepository devices;

    @BeforeEach
    void clean() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        mails = (RecordingMailSender) mailSender;
        mails.clear();
        devices = (RecordingTokenRepository) rememberMeTokens;
        devices.clear();
        ((MutableTestClock) clock).reset();
    }

    @Test
    void anUnknownPersonWithAVouchedAddressGetsAnAccountWithoutPassword() {
        IdentityUserDetails signedIn = signInService.signIn(
                claims("google", "sub-1", " Ida@Example.com ", true, "Ida", "Beispiel", Locale.ENGLISH), null);

        assertThat(signedIn.getUsername()).isEqualTo(EMAIL);
        assertThat(signedIn.getPassword()).isNull();
        assertThat(signedIn.getAuthorities()).extracting(Object::toString).contains("ROLE_USER");
        UserAccountDto account = userAccountService.findByEmail(EMAIL).orElseThrow();
        assertThat(account.hasPassword()).isFalse();
        assertThat(account.claimed()).isTrue();
        assertThat(account.emailVerified()).isTrue();
        assertThat(account.enabled()).isTrue();
        assertThat(account.firstName()).isEqualTo("Ida");
        assertThat(account.lastName()).isEqualTo("Beispiel");
        assertThat(account.locale()).isEqualTo(Locale.ENGLISH);
        assertThat(identityService.findAllOf(account.id()))
                .extracting(ExternalIdentityDto::registrationId, ExternalIdentityDto::email)
                .containsExactly(tuple("google", EMAIL));
        assertThat(identityService.findAllOf(account.id()).getFirst().lastUsedAt()).isNotNull();
    }

    @Test
    void anAddressTheProviderDoesNotVouchForLetsNobodyIn() {
        assertRefused(() -> signInService.signIn(claims("google", "sub-1", EMAIL, false), null),
                Reason.EMAIL_UNVERIFIED);
        assertRefused(() -> signInService.signIn(claims("google", "sub-1", null, false), null),
                Reason.EMAIL_UNVERIFIED);

        assertThat(userRepository.count()).isZero();
    }

    /** Once linked, the address plays no part: it may change at the provider, even to an unconfirmed one. */
    @Test
    void aKnownIdentityWinsOverTheAddressItReportsNow() {
        IdentityUserDetails first = signInService.signIn(claims("google", "sub-1", EMAIL, true), null);

        IdentityUserDetails second = signInService.signIn(claims("google", "sub-1", "other@example.com", false), null);

        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(second.getUsername()).as("the account keeps its own address").isEqualTo(EMAIL);
        assertThat(identityService.findAllOf(first.userId()).getFirst().email()).isEqualTo("other@example.com");
    }

    @Test
    void aConfirmedAccountWithThatAddressIsJoinedAndKeepsItsPassword() {
        UserAccountDto existing = userAccountService.createAccount(EMAIL, PASSWORD, "Ida", "Beispiel", true);

        IdentityUserDetails signedIn = signInService.signIn(claims("google", "sub-1", EMAIL, true), null);

        assertThat(signedIn.userId()).isEqualTo(existing.id());
        assertThat(signedIn.getPassword()).isNotNull();
        assertThat(identityService.findAllOf(existing.id())).hasSize(1);
    }

    @Test
    void joiningByAddressCanBeSwitchedOff() {
        userAccountService.createAccount(EMAIL, PASSWORD, "Ida", "Beispiel", true);
        ExternalSignInService strict = serviceWith(
                IdentityProperties.defaults().withOAuth2(OAuth2Settings.defaults().linkByEmail(false)));

        assertRefused(() -> inTransaction(() -> strict.signIn(claims("google", "sub-1", EMAIL, true), null)),
                Reason.NOT_LINKED);
    }

    /**
     * Pre-account takeover: somebody registers with another person's address
     * and waits. When the owner arrives through a provider that vouches for
     * the address, the password set at the registration must be gone -- and
     * with it every device signed in under it.
     */
    @Test
    void aPasswordSetBeforeTheAddressWasConfirmedIsDropped() {
        registrationService.register(EMAIL, PASSWORD, "Ida", "Beispiel");
        String verificationToken = mails.tokenFromLastMailTo(EMAIL);
        devices.rememberDevice(EMAIL, "attackers-laptop");

        IdentityUserDetails signedIn = signInService.signIn(claims("google", "sub-1", EMAIL, true), null);

        assertThat(signedIn.getPassword()).isNull();
        UserAccountDto account = userAccountService.findByEmail(EMAIL).orElseThrow();
        assertThat(account.hasPassword()).isFalse();
        assertThat(account.emailVerified()).isTrue();
        assertThat(account.enabled()).isTrue();
        assertThat(devices.seriesOf(EMAIL)).as("PasswordChanged discards the remembered devices").isEmpty();
        assertThatThrownBy(() -> inTransaction(() -> tokenIssuer.redeem(verificationToken,
                AuthTokenType.EMAIL_VERIFICATION)))
                .as("the old verification link leads nowhere any more")
                .isInstanceOf(IdentityException.class);
    }

    @Test
    void anOpenInvitationIsRedeemedByTheVouchedAddress() {
        UserAccountDto invited = invitationService.inviteNewAccount(EMAIL, "Ida", "Beispiel");
        String invitationToken = mails.tokenFromLastMailTo(EMAIL);

        IdentityUserDetails signedIn = signInService.signIn(claims("google", "sub-1", EMAIL, true), null);

        assertThat(signedIn.userId()).isEqualTo(invited.id());
        UserAccountDto account = userAccountService.findById(invited.id()).orElseThrow();
        assertThat(account.claimed()).isTrue();
        assertThat(account.hasPassword()).isFalse();
        assertThatThrownBy(() -> invitationService.claim(invitationToken, PASSWORD))
                .as("the invitation is settled")
                .isInstanceOf(IdentityException.class);
        assertThatThrownBy(() -> invitationService.resendInvitation(invited.id()))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.ACCOUNT_ALREADY_CLAIMED);
    }

    /** The token proves the invited mailbox; the provider's address may differ. */
    @Test
    void anInvitationTokenLinksWhateverAddressTheProviderReports() {
        UserAccountDto invited = invitationService.inviteNewAccount(EMAIL, "Ida", "Beispiel");
        String invitationToken = mails.tokenFromLastMailTo(EMAIL);

        IdentityUserDetails signedIn = signInService.signIn(
                claims("github", "42", "ida.private@example.org", false), invitationToken);

        assertThat(signedIn.userId()).isEqualTo(invited.id());
        assertThat(signedIn.getUsername()).isEqualTo(EMAIL);
        UserAccountDto account = userAccountService.findById(invited.id()).orElseThrow();
        assertThat(account.claimed()).isTrue();
        assertThat(account.emailVerified()).isTrue();
    }

    @Test
    void anUnknownInvitationIsRefused() {
        assertRefused(() -> signInService.signIn(claims("google", "sub-1", EMAIL, true), "no-such-token"),
                Reason.INVITATION_INVALID);
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void withoutSelfRegistrationNobodyUnknownGetsIn() {
        ExternalSignInService closed = serviceWith(
                IdentityProperties.defaults().withOAuth2(OAuth2Settings.defaults().createAccounts(false)));

        assertRefused(() -> inTransaction(() -> closed.signIn(claims("google", "sub-1", EMAIL, true), null)),
                Reason.NO_ACCOUNT);
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void aDisabledAccountIsRefused() {
        IdentityUserDetails first = signInService.signIn(claims("google", "sub-1", EMAIL, true), null);
        userAccountService.setEnabled(first.userId(), false);

        assertRefused(() -> signInService.signIn(claims("google", "sub-1", EMAIL, true), null), Reason.DISABLED);
    }

    @Test
    void anAccountWithAnotherIdentityAtTheProviderIsNotJoined() {
        signInService.signIn(claims("google", "sub-1", EMAIL, true), null);

        assertRefused(() -> signInService.signIn(claims("google", "sub-2", EMAIL, true), null),
                Reason.ALREADY_LINKED);
    }

    /** A provider proves possession more strongly than the reset mail, which lifts the lock too. */
    @Test
    void aSignInThroughAProviderLiftsATemporaryLock() {
        UserAccountDto account = userAccountService.createAccount(EMAIL, PASSWORD, "Ida", "Beispiel", true);
        inTransaction(() -> {
            UserAccount user = userRepository.findById(account.id()).orElseThrow();
            for (int i = 0; i < 3; i++) {
                user.recordFailedLogin(clock.instant(), LoginProtectionSettings.defaults());
            }
            return user;
        });
        assertThat(userAccountService.findById(account.id()).orElseThrow().lockedAt(clock.instant())).isTrue();

        signInService.signIn(claims("google", "sub-1", EMAIL, true), null);

        assertThat(userAccountService.findById(account.id()).orElseThrow().lockedUntil()).isNull();
    }

    @Test
    void aSignedInAccountLinksAnotherProviderWhateverItsAddress() {
        UserAccountDto account = userAccountService.createAccount(EMAIL, PASSWORD, "Ida", "Beispiel", true);

        IdentityUserDetails linked = signInService.link(account.id(), claims("github", "42", null, false));

        assertThat(linked.userId()).isEqualTo(account.id());
        assertThat(identityService.findAllOf(account.id()))
                .extracting(ExternalIdentityDto::registrationId)
                .containsExactly("github");
        assertThat(identityService.accountsWithExternalIdentities(List.of(account.id(), -1L)))
                .containsExactly(account.id());
    }

    @Test
    void anIdentityOfAnotherAccountIsNotLinked() {
        signInService.signIn(claims("google", "sub-1", EMAIL, true), null);
        UserAccountDto other = userAccountService.createAccount("other@example.com", PASSWORD, "O", "Ther", true);

        assertRefused(() -> signInService.link(other.id(), claims("google", "sub-1", EMAIL, true)),
                Reason.ALREADY_LINKED);
    }

    @Test
    void theLastWayInCannotBeUnlinked() {
        IdentityUserDetails signedIn = signInService.signIn(claims("google", "sub-1", EMAIL, true), null);

        assertThatThrownBy(() -> identityService.unlink(signedIn.userId(), "google"))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.LAST_SIGN_IN_METHOD);
        assertThatThrownBy(() -> identityService.unlink(signedIn.userId(), "github"))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.EXTERNAL_IDENTITY_NOT_FOUND);
    }

    @Test
    void unlinkingAWayInThrowsTheRememberedDevicesOut() {
        UserAccountDto account = userAccountService.createAccount(EMAIL, PASSWORD, "Ida", "Beispiel", true);
        signInService.link(account.id(), claims("google", "sub-1", EMAIL, true));
        devices.rememberDevice(EMAIL, "phone");

        identityService.unlink(account.id(), "google");

        assertThat(identityService.findAllOf(account.id())).isEmpty();
        assertThat(devices.seriesOf(EMAIL)).isEmpty();
        assertThat(identityRepository.count()).isZero();
    }

    /**
     * Remember-me, passkeys and the session refresh load through the
     * {@code UserDetailsService}; the password form must not accept the
     * account, and must answer as for an unknown address.
     */
    @Test
    void anAccountWithoutPasswordIsHandedOutButNotToThePasswordForm() {
        signInService.signIn(claims("google", "sub-1", EMAIL, true), null);

        UserDetails loaded = userDetailsService.loadUserByUsername(EMAIL);
        assertThat(loaded.getPassword()).isNull();
        assertThatThrownBy(() -> IdentityBeans.passwordAccountsOnly(userDetailsService).loadUserByUsername(EMAIL))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void anOpenInvitationIsStillNotHandedOut() {
        invitationService.inviteNewAccount(EMAIL, "Ida", "Beispiel");

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(EMAIL))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void anAccountWithoutPasswordCannotBeForcedToChangeIt() {
        IdentityUserDetails signedIn = signInService.signIn(claims("google", "sub-1", EMAIL, true), null);

        assertThatThrownBy(() -> userAccountService.requirePasswordChange(signedIn.userId()))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.ACCOUNT_WITHOUT_PASSWORD);
    }

    /** "Forgot password" adds a password: the link proves the mailbox, and a second way in harms nobody. */
    @Test
    void forgotPasswordAddsAPassword() {
        signInService.signIn(claims("google", "sub-1", EMAIL, true), null);

        passwordResetService.requestReset(EMAIL);
        passwordResetService.resetPassword(mails.tokenFromLastMailTo(EMAIL), PASSWORD);

        UserAccountDto account = userAccountService.findByEmail(EMAIL).orElseThrow();
        assertThat(account.hasPassword()).isTrue();
        assertThat(identityService.findAllOf(account.id())).hasSize(1);
    }

    private ExternalSignInService serviceWith(IdentityProperties properties) {
        return new ExternalSignInServiceImpl(userRepository, identityRepository, roleRepository, tokenIssuer,
                tokenRepository, properties, clock, context, context.getBeanProvider(LoginThrottle.class));
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private static void assertRefused(Runnable signIn, Reason reason) {
        assertThatThrownBy(signIn::run)
                .isInstanceOf(ExternalSignInException.class)
                .extracting(e -> ((ExternalSignInException) e).getReason())
                .isEqualTo(reason);
    }

    private static ExternalIdentityClaims claims(String registrationId, String subject, @Nullable String email,
                                                 boolean verified) {
        return claims(registrationId, subject, email, verified, "Ida", "Beispiel", null);
    }

    private static ExternalIdentityClaims claims(String registrationId, String subject, @Nullable String email,
                                                 boolean verified, @Nullable String given, @Nullable String family,
                                                 @Nullable Locale locale) {
        return new ExternalIdentityClaims(registrationId, subject, email, verified, given, family, null, locale);
    }
}
