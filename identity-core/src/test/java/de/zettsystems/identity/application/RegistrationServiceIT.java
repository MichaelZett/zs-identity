package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.AbstractIdentityIntegrationTest;
import de.zettsystems.identity.testsupport.MutableTestClock;
import de.zettsystems.identity.testsupport.RecordingMailSender;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.AccountRegistered;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.RegistrationMode;
import de.zettsystems.identity.values.UserAccountDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RecordApplicationEvents
class RegistrationServiceIT extends AbstractIdentityIntegrationTest {

    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private InvitationService invitationService;
    @Autowired
    private PasswordResetService passwordResetService;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private ApplicationEvents events;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private AuthTokenRepository tokenRepository;
    @Autowired
    private IdentityMailSender mailSender;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private Clock clock;

    private RecordingMailSender mails;
    private MutableTestClock testClock;

    @BeforeEach
    void resetState() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        mails = (RecordingMailSender) mailSender;
        mails.clear();
        testClock = (MutableTestClock) clock;
        testClock.reset();
    }

    @Test
    void registrationCreatesLockedAccountAndSendsVerificationMail() {
        UserAccountDto created = registrationService.register(
                "Anna.Beispiel@Example.COM", "ein-langes-passwort", "Anna", "Beispiel");

        assertThat(created.email())
                .as("addresses are stored in lower case, so that the same address "
                        + "in anderer Schreibweise kein zweites Konto ergibt")
                .isEqualTo("anna.beispiel@example.com");
        assertThat(created.enabled()).isFalse();
        assertThat(created.emailVerified()).isFalse();
        assertThat(created.roleCodes()).containsExactly("USER");
        assertThat(mails.sentMails()).hasSize(1);
        assertThat(mails.lastMailTo("anna.beispiel@example.com")).isPresent();
    }

    @Test
    void confirmingTheMailedTokenUnlocksTheAccount() {
        registrationService.register("bea@example.com", "ein-langes-passwort", "Bea", "Beispiel");
        String token = mails.tokenFromLastMailTo("bea@example.com");

        UserAccountDto confirmed = registrationService.confirmEmail(token);

        assertThat(confirmed.enabled()).isTrue();
        assertThat(confirmed.emailVerified()).isTrue();
    }

    @Test
    void aSecondUseOfTheSameTokenReportsSuccessOnceTheAccountIsVerified() {
        // Mail scanners often redeem the link before the human does, so the
        // second visit must not end in an error; confirmed is confirmed.
        registrationService.register("carl@example.com", "ein-langes-passwort", "Carl", "Beispiel");
        String token = mails.tokenFromLastMailTo("carl@example.com");
        registrationService.confirmEmail(token);

        UserAccountDto secondUse = registrationService.confirmEmail(token);

        assertThat(secondUse.enabled()).isTrue();
        assertThat(secondUse.emailVerified()).isTrue();
    }

    @Test
    void anInvalidatedTokenOfAVerifiedAccountAlsoReportsSuccess() {
        registrationService.register("carla@example.com", "ein-langes-passwort", "Carla", "Beispiel");
        String firstToken = mails.tokenFromLastMailTo("carla@example.com");
        registrationService.resendVerification("carla@example.com");
        registrationService.confirmEmail(mails.tokenFromLastMailTo("carla@example.com"));

        assertThat(registrationService.confirmEmail(firstToken).emailVerified()).isTrue();
    }

    @Test
    void aTokenStopsWorkingAfterItsValidityPeriod() {
        registrationService.register("dora@example.com", "ein-langes-passwort", "Dora", "Beispiel");
        String token = mails.tokenFromLastMailTo("dora@example.com");

        testClock.advanceBy(Duration.ofHours(25));

        assertThatThrownBy(() -> registrationService.confirmEmail(token))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.TOKEN_EXPIRED);
    }

    @Test
    void anUnknownTokenIsRejected() {
        assertThatThrownBy(() -> registrationService.confirmEmail("gibt-es-nicht"))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.TOKEN_INVALID);
    }

    @Test
    void theSameAddressCannotRegisterTwice() {
        registrationService.register("emil@example.com", "ein-langes-passwort", "Emil", "Beispiel");

        assertThatThrownBy(() -> registrationService.register(
                "EMIL@example.com", "ein-anderes-passwort", "Emil", "Zweitkonto"))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    void tooShortPasswordsAreRejected() {
        assertThatThrownBy(() -> registrationService.register("frida@example.com", "kurz", "Frida", "Beispiel"))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.PASSWORD_TOO_SHORT);
    }

    @Test
    void passwordsAreStoredHashed() {
        registrationService.register("gustav@example.com", "ein-langes-passwort", "Gustav", "Beispiel");

        String stored = userRepository.findByEmail("gustav@example.com").orElseThrow().getPasswordHash();

        assertThat(stored).isNotEqualTo("ein-langes-passwort");
        assertThat(passwordEncoder.matches("ein-langes-passwort", stored)).isTrue();
    }

    @Test
    void resendingReplacesTheEarlierTokenSoOnlyTheNewestLinkWorks() {
        registrationService.register("hanna@example.com", "ein-langes-passwort", "Hanna", "Beispiel");
        String firstToken = mails.tokenFromLastMailTo("hanna@example.com");

        registrationService.resendVerification("hanna@example.com");
        String secondToken = mails.tokenFromLastMailTo("hanna@example.com");

        assertThat(secondToken).isNotEqualTo(firstToken);
        assertThatThrownBy(() -> registrationService.confirmEmail(firstToken))
                .as("the link sent before has to have become invalid")
                .isInstanceOf(IdentityException.class);
        assertThat(registrationService.confirmEmail(secondToken).enabled()).isTrue();
    }

    /**
     * The language of the registration is the only thing the person says about
     * it; when mail is sent later there is no browser left to ask.
     */
    @Test
    void registrationRemembersTheLanguageItHappenedIn() {
        UserAccountDto created = registrationService.register("ines@example.com", "ein-langes-passwort",
                AccountName.of("Ines", "Beispiel"), Locale.ENGLISH);

        assertThat(created.locale()).isEqualTo(Locale.ENGLISH);
        assertThat(userRepository.findByEmail("ines@example.com").orElseThrow().getLocale())
                .isEqualTo(Locale.ENGLISH);
    }

    @Test
    void withoutALanguageTheAccountKeepsNoneAndFallsBackToTheApplication() {
        UserAccountDto created = registrationService.register("jan@example.com", "ein-langes-passwort",
                AccountName.of("Jan", "Beispiel"), null);

        assertThat(created.locale())
                .as("null means \"no choice of its own\", not \"English\"")
                .isNull();
        assertThat(created.localeOr(Locale.GERMAN)).isEqualTo(Locale.GERMAN);
    }

    @Test
    void resendingForAnUnknownAddressStaysSilent() {
        assertThat(mails.sentMails()).isEmpty();

        registrationService.resendVerification("gibt-es-nicht@example.com");

        assertThat(mails.sentMails())
                .as("any feedback would reveal who has an account with us")
                .isEmpty();
    }

    @Test
    void withoutFurtherSettingsAnybodyMayRegister() {
        assertThat(registrationService.registrationMode()).isEqualTo(RegistrationMode.OPEN);
    }

    @Test
    void theRegistrationIsAnnouncedWhenTheAddressIsConfirmedNotWhenTheFormIsSent() {
        UserAccountDto created = registrationService.register(
                "karl@example.com", "ein-langes-passwort", "Karl", "Beispiel");
        assertThat(events.stream(AccountRegistered.class))
                .as("the account is not usable yet")
                .isEmpty();

        String token = mails.tokenFromLastMailTo("karl@example.com");
        registrationService.confirmEmail(token);
        registrationService.confirmEmail(token);

        assertThat(events.stream(AccountRegistered.class))
                .as("once, without a code, and not again for the repeated link")
                .containsExactly(new AccountRegistered(created.id(), "karl@example.com", null));
        assertThat(userRepository.findByEmail("karl@example.com").orElseThrow().isRegistrationPending()).isFalse();
    }

    @Test
    void aPasswordResetProvesTheAddressAndCompletesTheRegistration() {
        UserAccountDto created = registrationService.register(
                "lena@example.com", "ein-langes-passwort", "Lena", "Beispiel");

        passwordResetService.requestReset("lena@example.com");
        passwordResetService.resetPassword(mails.tokenFromLastMailTo("lena@example.com"), "ein-neues-passwort");

        assertThat(events.stream(AccountRegistered.class))
                .containsExactly(new AccountRegistered(created.id(), "lena@example.com", null));
    }

    @Test
    void anAdministratorEnablingThePendingAccountCompletesTheRegistration() {
        UserAccountDto created = registrationService.register(
                "malte@example.com", "ein-langes-passwort", "Malte", "Beispiel");

        userAccountService.setEnabled(created.id(), true);
        userAccountService.setEnabled(created.id(), true);

        assertThat(events.stream(AccountRegistered.class)).hasSize(1);
    }

    @Test
    void invitationsAreNotRegistrations() {
        invitationService.inviteNewAccount("nora@example.com", AccountName.of("Nora", "Beispiel"));
        invitationService.claim(mails.tokenFromLastMailTo("nora@example.com"), "ein-langes-passwort");

        assertThat(events.stream(AccountRegistered.class)).isEmpty();
    }

    @Test
    void anAddressWithAnOpenInvitationIsSentToItsMailInsteadOfGettingASecondAccount() {
        UserAccountDto managed = userAccountService.createManagedAccount(AccountName.of("Otto", "Beispiel"));
        invitationService.inviteToClaim(managed.id(), "otto@example.com");

        assertThatThrownBy(() -> registrationService.register(
                "Otto@Example.com", "ein-langes-passwort", "Otto", "Doppelt"))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.INVITATION_PENDING);
        assertThat(userRepository.findByEmail("otto@example.com").orElseThrow().getId())
                .as("still the one managed account, not a second one")
                .isEqualTo(managed.id());
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void aCodeIsCheckedEvenWhereNoneIsNeededAndWithoutAGateNoneIsAdmitted() {
        AccountName name = AccountName.of("Paula", "Beispiel");

        assertThatThrownBy(() -> registrationService.register(
                "paula@example.com", "ein-langes-passwort", name, null, "ERFUNDEN"))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.INVITATION_CODE_INVALID);
        assertThat(userRepository.findByEmail("paula@example.com")).isEmpty();
        assertThat(registrationService.register("paula@example.com", "ein-langes-passwort", name, null, "  ")
                .email())
                .as("a blank code is no code")
                .isEqualTo("paula@example.com");
    }
}
