package de.zettsystems.identity.application;

import de.zettsystems.identity.testsupport.IdentityTestApplication;
import de.zettsystems.identity.testsupport.PostgresTestImage;
import de.zettsystems.identity.testsupport.RecordingMailSender;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.RegistrationMode;
import de.zettsystems.identity.values.UserAccountDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Shows that the building block really is configurable and not quietly tailored
 * to one application: with
 * {@code zs.identity.self-registration-enabled=false} it rejects every
 * self-registration, and the UI can hide the link to it. Invitations still
 * work, with or without a code: they are the way in when nobody may register.
 *
 * <p>A context of its own with a different property, which is why this does not
 * derive from {@code AbstractIdentityIntegrationTest}.
 */
@SpringBootTest(classes = IdentityTestApplication.class,
        properties = "zs.identity.self-registration-enabled=false")
class SelfRegistrationDisabledIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresTestImage.resolve())
            .withDatabaseName("identity")
            .withUsername("app")
            .withPassword("app")
            .withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private InvitationService invitationService;
    @Autowired
    private IdentityMailSender mailSender;

    @Test
    void theUiCanTellThatRegistrationIsOff() {
        assertThat(registrationService.isSelfRegistrationEnabled()).isFalse();
        assertThat(registrationService.registrationMode())
                .as("the older switch means CLOSED")
                .isEqualTo(RegistrationMode.CLOSED);
    }

    @Test
    void aCodeDoesNotOpenAClosedRegistration() {
        AccountName name = AccountName.of("Mit", "Code");

        assertThatThrownBy(() -> registrationService.register(
                "mit-code@example.com", "ein-langes-passwort", name, null, "IRGENDWAS"))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.SELF_REGISTRATION_DISABLED);
    }

    @Test
    void invitationsStillLeadIntoANewAccount() {
        RecordingMailSender mails = (RecordingMailSender) mailSender;
        invitationService.inviteNewAccount("eingeladen@example.com", AccountName.of("Ein", "Geladen"));

        UserAccountDto claimed = invitationService.claim(
                mails.tokenFromLastMailTo("eingeladen@example.com"), "ein-langes-passwort");

        assertThat(claimed.enabled()).isTrue();
    }

    @Test
    void aManagedAccountCanStillBeInvitedAndInvitedAgain() {
        RecordingMailSender mails = (RecordingMailSender) mailSender;
        UserAccountDto managed = userAccountService.createManagedAccount(AccountName.of("Ver", "Waltet"));
        invitationService.inviteToClaim(managed.id(), "verwaltet@example.com");
        invitationService.resendInvitation(managed.id());

        UserAccountDto claimed = invitationService.claim(
                mails.tokenFromLastMailTo("verwaltet@example.com"), "ein-langes-passwort");

        assertThat(claimed.id()).isEqualTo(managed.id());
        assertThat(claimed.enabled()).isTrue();
    }

    @Test
    void registeringIsRefused() {
        assertThatThrownBy(() -> registrationService.register(
                "abgelehnt@example.com", "ein-langes-passwort", "Abge", "Lehnt"))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.SELF_REGISTRATION_DISABLED);
    }

    @Test
    void anAdministrationCanStillCreateAccounts() {
        var created = userAccountService.createAccount(
                "von-hand@example.com", "ein-langes-passwort", "Von", "Hand", true);

        assertThat(created.enabled())
                .as("switching it off must not block the route through administration")
                .isTrue();
    }
}
