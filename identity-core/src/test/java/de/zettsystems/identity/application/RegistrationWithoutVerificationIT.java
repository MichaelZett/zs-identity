package de.zettsystems.identity.application;

import de.zettsystems.identity.testsupport.IdentityTestApplication;
import de.zettsystems.identity.testsupport.PostgresTestImage;
import de.zettsystems.identity.testsupport.RecordingMailSender;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.AccountRegistered;
import de.zettsystems.identity.values.UserAccountDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With {@code zs.identity.email-verification-required=false} there is no mail
 * to wait for: the account is usable at once, and that is the moment the
 * application hears about it.
 *
 * <p>A context of its own with a different property, which is why this does not
 * derive from {@code AbstractIdentityIntegrationTest}.
 */
@SpringBootTest(classes = IdentityTestApplication.class,
        properties = "zs.identity.email-verification-required=false")
@RecordApplicationEvents
class RegistrationWithoutVerificationIT {

    private static final String EMAIL = "ohne-bestaetigung@example.com";

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
    private RecordingMailSender mails;
    @Autowired
    private ApplicationEvents events;

    @Test
    void theAccountIsUsableAtOnceAndNoMailGoesOut() {
        UserAccountDto account = registrationService.register(EMAIL, "ein-langes-passwort",
                AccountName.of("Ohne", "Bestaetigung"));

        assertThat(account.enabled()).isTrue();
        assertThat(mails.lastMailTo(EMAIL)).isEmpty();
        assertThat(events.stream(AccountRegistered.class))
                .extracting(AccountRegistered::userId)
                .containsExactly(account.id());
    }
}
