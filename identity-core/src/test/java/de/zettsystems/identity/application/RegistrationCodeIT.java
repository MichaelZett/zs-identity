package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.IdentityTestApplication;
import de.zettsystems.identity.testsupport.PostgresTestImage;
import de.zettsystems.identity.testsupport.RecordingMailSender;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.AccountRegistered;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.RegistrationMode;
import de.zettsystems.identity.values.UserAccountDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Registration only with an invitation code ({@code registration-mode=CODE},
 * since 1.5.0): the application's {@link RegistrationGate} decides, the
 * building block asks it before an account exists and hands the code back
 * with {@link AccountRegistered} once the account is usable. Invitations an
 * administrator sends need no code.
 *
 * <p>A context of its own with a different property, which is why this does
 * not derive from {@code AbstractIdentityIntegrationTest}.
 */
@SpringBootTest(classes = IdentityTestApplication.class,
        properties = "zs.identity.registration-mode=CODE")
@Import(RegistrationCodeIT.Codes.class)
@RecordApplicationEvents
class RegistrationCodeIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresTestImage.resolve())
            .withDatabaseName("identity")
            .withUsername("app")
            .withPassword("app")
            .withReuse(true);

    private static final String PASSWORD = "ein-langes-passwort";

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** What an application brings: it knows its codes, the building block does not. */
    @TestConfiguration
    static class Codes {
        @Bean
        RegistrationGate registrationGate() {
            return code -> Set.of("JUGEND-24", "ERWACHSENE").contains(code);
        }
    }

    @Autowired
    private RegistrationService registrationService;
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
    private ApplicationEvents events;

    private RecordingMailSender mails;

    @BeforeEach
    void resetState() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        mails = (RecordingMailSender) mailSender;
        mails.clear();
    }

    @Test
    void theFormIsOfferedAndAsksForACode() {
        assertThat(registrationService.isSelfRegistrationEnabled()).isTrue();
        assertThat(registrationService.registrationMode()).isEqualTo(RegistrationMode.CODE);
    }

    @Test
    void withoutACodeNoAccountIsCreated() {
        AccountName name = AccountName.of("Anna", "Ohne");

        assertThatThrownBy(() -> registrationService.register("anna@example.com", PASSWORD, name, null))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.INVITATION_CODE_REQUIRED);
        assertThat(userRepository.count()).isZero();
        assertThat(mails.sentMails()).isEmpty();
    }

    @Test
    void anUnknownCodeIsTurnedDownBeforeAnAccountExists() {
        AccountName name = AccountName.of("Bert", "Falsch");

        assertThatThrownBy(() -> registrationService.register("bert@example.com", PASSWORD, name, null, "RATEN"))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.INVITATION_CODE_INVALID);
        assertThat(userRepository.count()).isZero();
        assertThat(mails.sentMails()).isEmpty();
    }

    @Test
    void withoutAValidCodeNobodyLearnsWhetherAnAddressHasAnAccount() {
        registrationService.register("clara@example.com", PASSWORD, AccountName.of("Clara", "Da"), null, "ERWACHSENE");
        AccountName name = AccountName.of("Clara", "Fremd");

        assertThatThrownBy(() -> registrationService.register("clara@example.com", PASSWORD, name, null, "RATEN"))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .as("the code is checked first, so the answer says nothing about the address")
                .isEqualTo(IdentityMessageKeys.INVITATION_CODE_INVALID);
    }

    @Test
    void theCodeWaitsOnThePendingAccountAndComesBackOnceItIsUsable() {
        UserAccountDto created = registrationService.register(
                "dora@example.com", PASSWORD, AccountName.of("Dora", "Jung"), null, "  JUGEND-24 ");

        UserAccount pending = userRepository.findByEmail("dora@example.com").orElseThrow();
        assertThat(pending.isRegistrationPending()).isTrue();
        assertThat(pending.getRegistrationCode()).as("without the blanks around it").isEqualTo("JUGEND-24");
        assertThat(events.stream(AccountRegistered.class))
                .as("nothing yet: the address is not confirmed")
                .isEmpty();

        registrationService.confirmEmail(mails.tokenFromLastMailTo("dora@example.com"));

        assertThat(events.stream(AccountRegistered.class))
                .containsExactly(new AccountRegistered(created.id(), "dora@example.com", "JUGEND-24"));
        UserAccount completed = userRepository.findByEmail("dora@example.com").orElseThrow();
        assertThat(completed.isRegistrationPending()).isFalse();
        assertThat(completed.getRegistrationCode()).as("forgotten once handed over").isNull();
    }

    @Test
    void anInvitationForANewAccountNeedsNoCode() {
        invitationService.inviteNewAccount("emil@example.com", AccountName.of("Emil", "Eingeladen"));

        UserAccountDto claimed = invitationService.claim(mails.tokenFromLastMailTo("emil@example.com"), PASSWORD);

        assertThat(claimed.enabled()).isTrue();
        assertThat(events.stream(AccountRegistered.class)).isEmpty();
    }

    @Test
    void aManagedAccountCanBeInvitedAndInvitedAgainWithoutACode() {
        UserAccountDto managed = userAccountService.createManagedAccount(AccountName.of("Frieda", "Verwaltet"));
        invitationService.inviteToClaim(managed.id(), "frieda@example.com");
        invitationService.resendInvitation(managed.id());

        UserAccountDto claimed = invitationService.claim(mails.tokenFromLastMailTo("frieda@example.com"), PASSWORD);

        assertThat(claimed.id()).isEqualTo(managed.id());
        assertThat(claimed.enabled()).isTrue();
    }
}
