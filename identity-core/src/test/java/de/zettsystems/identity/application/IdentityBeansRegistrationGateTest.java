package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.ExternalIdentityRepository;
import de.zettsystems.identity.domain.PasskeyRepository;
import de.zettsystems.identity.domain.RoleRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.RegistrationMode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The {@link RegistrationGate} is optional -- except in the mode {@code CODE},
 * where a missing one would turn every registration down. That is learnt at
 * startup, not from the first person who tries (since 1.5.0).
 */
class IdentityBeansRegistrationGateTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(IdentityBeans.class)
            .withBean(UserAccountRepository.class, () -> mock(UserAccountRepository.class))
            .withBean(RoleRepository.class, () -> mock(RoleRepository.class))
            .withBean(AuthTokenRepository.class, () -> mock(AuthTokenRepository.class))
            .withBean(PasskeyRepository.class, () -> mock(PasskeyRepository.class))
            .withBean(ExternalIdentityRepository.class, () -> mock(ExternalIdentityRepository.class))
            .withBean(EntityManager.class, () -> mock(EntityManager.class))
            .withBean(IdentityMailSender.class, () -> mock(IdentityMailSender.class));

    @Test
    void theModeCodeDoesNotStartWithoutAGate() {
        contextRunner
                .withBean(IdentityProperties.class, () -> inMode(RegistrationMode.CODE))
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("RegistrationGate"));
    }

    @Test
    void theModeCodeStartsWithAGate() {
        contextRunner
                .withBean(IdentityProperties.class, () -> inMode(RegistrationMode.CODE))
                .withBean(RegistrationGate.class, () -> code -> true)
                .run(context -> assertThat(context.getBean(RegistrationService.class).registrationMode())
                        .isEqualTo(RegistrationMode.CODE));
    }

    @Test
    void theOtherModesNeedNoGate() {
        contextRunner
                .withBean(IdentityProperties.class, () -> inMode(RegistrationMode.CLOSED))
                .run(context -> assertThat(context).hasNotFailed());
        contextRunner
                .withBean(IdentityProperties.class, IdentityProperties::defaults)
                .run(context -> assertThat(context).hasNotFailed());
    }

    private static IdentityProperties inMode(RegistrationMode mode) {
        return IdentityProperties.defaults().withRegistrationMode(mode);
    }
}
