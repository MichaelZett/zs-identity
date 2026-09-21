package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.AbstractIdentityIntegrationTest;
import de.zettsystems.identity.testsupport.RecordingMailSender;
import de.zettsystems.identity.testsupport.RecordingTokenRepository;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.UserAccountDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The route the whole thing exists for: an application keeps its people signed
 * in for weeks with remember-me, and whoever loses a phone changes their
 * password. After that the phone has to be out -- without the application
 * writing a line for it.
 *
 * <p>The building block's services publish the events, the
 * {@code RememberMeTokenCleaner} listens, and the application's
 * {@code PersistentTokenRepository} (here
 * {@link RecordingTokenRepository}) is what is cleared.
 */
class SessionTokenCleanupIT extends AbstractIdentityIntegrationTest {

    private static final String EMAIL = "ida@example.com";
    private static final String PASSWORD = "das-alte-passwort";
    private static final String NEW_PASSWORD = "das-neue-passwort";

    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private PasswordResetService passwordResetService;
    @Autowired
    private InvitationService invitationService;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private AuthTokenRepository tokenRepository;
    @Autowired
    private IdentityMailSender mailSender;
    @Autowired
    private PersistentTokenRepository rememberMe;
    @Autowired
    private TransactionTemplate transactions;

    private RecordingMailSender mails;
    private RecordingTokenRepository devices;
    private UserAccountDto account;

    @BeforeEach
    void registerSomebodyWhoIsSignedInOnTwoDevices() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        mails = (RecordingMailSender) mailSender;
        mails.clear();
        devices = (RecordingTokenRepository) rememberMe;
        devices.clear();

        account = registrationService.register(EMAIL, PASSWORD, "Ida", "Beispiel");
        registrationService.confirmEmail(mails.tokenFromLastMailTo(EMAIL));
        mails.clear();

        devices.rememberDevice(EMAIL, "phone");
        devices.rememberDevice(EMAIL, "tablet");
    }

    @Test
    void changingThePasswordThrowsTheRememberedDevicesOut() {
        userAccountService.changePassword(account.id(), NEW_PASSWORD);

        assertThat(devices.seriesOf(EMAIL)).isEmpty();
    }

    /** The more common route: the phone is gone, so the password goes through "forgot password". */
    @Test
    void resettingThePasswordThroughTheMailDoesTheSame() {
        passwordResetService.requestReset(EMAIL);
        passwordResetService.resetPassword(mails.tokenFromLastMailTo(EMAIL), NEW_PASSWORD);

        assertThat(devices.seriesOf(EMAIL)).isEmpty();
    }

    @Test
    void lockingAnAccountTakesItsDevicesWithIt() {
        userAccountService.setEnabled(account.id(), false);

        assertThat(devices.seriesOf(EMAIL)).isEmpty();
    }

    @Test
    void deletingAnAccountLeavesNoTokensBehind() {
        userAccountService.deleteAccount(account.id());

        assertThat(devices.seriesOf(EMAIL)).isEmpty();
    }

    /** Nothing happened, so nothing is cleared -- otherwise every save would sign people out. */
    @Test
    void renamingAnAccountLeavesTheDevicesAlone() {
        userAccountService.rename(account.id(), AccountName.of("Ida", "Anders"));
        userAccountService.setEnabled(account.id(), true);

        assertThat(devices.seriesOf(EMAIL)).containsExactly("phone", "tablet");
    }

    /**
     * An invitation that is redeemed puts the address on the account and gives
     * it its first password. Neither can have left tokens behind -- a managed
     * account cannot sign in -- and the devices of the person who did the
     * inviting must not be touched.
     */
    @Test
    void invitingAndRedeemingDoesNotDisturbAnybodyElse() {
        UserAccountDto managed = userAccountService.createManagedAccount(AccountName.of("Otto", "Neu"));
        invitationService.inviteToClaim(managed.id(), "otto@example.com");
        invitationService.claim(mails.tokenFromLastMailTo("otto@example.com"), "ein-langes-passwort");

        assertThat(devices.seriesOf("otto@example.com")).isEmpty();
        assertThat(devices.seriesOf(EMAIL)).containsExactly("phone", "tablet");
    }

    /**
     * The reason the cleaner listens {@code AFTER_COMMIT}: a change that is
     * rolled back afterwards has thrown nobody out. Were the tokens removed
     * inside the transaction, they would be gone while the password still is
     * the old one.
     */
    @Test
    void aChangeThatIsRolledBackThrowsNobodyOut() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            userAccountService.changePassword(account.id(), NEW_PASSWORD);
            throw new IllegalStateException("something goes wrong afterwards");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(devices.seriesOf(EMAIL)).containsExactly("phone", "tablet");
    }
}
