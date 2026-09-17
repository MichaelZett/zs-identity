package de.zettsystems.identity.application;

import de.zettsystems.identity.testsupport.IdentityTestApplication;
import de.zettsystems.identity.testsupport.PostgresTestImage;
import de.zettsystems.identity.values.IdentityMessageKeys;
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
 * self-registration, and the UI can hide the link to it.
 *
 * <p>A context of its own with a different property, which is why this does not
 * derive from {@code AbstractIdentityIntegrationTest}.
 */
@SpringBootTest(classes = IdentityTestApplication.class,
        properties = "zs.identity.self-registration-enabled=false")
class SelfRegistrationDisabledIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresTestImage.resolve())
            .withDatabaseName("identity")
            .withUsername("identity")
            .withPassword("identity")
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

    @Test
    void theUiCanTellThatRegistrationIsOff() {
        assertThat(registrationService.isSelfRegistrationEnabled()).isFalse();
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
