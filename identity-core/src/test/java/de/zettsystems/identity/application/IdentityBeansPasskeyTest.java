package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.ExternalIdentityRepository;
import de.zettsystems.identity.domain.PasskeyRepository;
import de.zettsystems.identity.domain.RoleRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.values.IdentityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@code spring-security-webauthn} is optional (since 0.11.0): it only hangs
 * {@code compileOnly} off the building block.
 *
 * <p>This is the proof, as with the mail library: in our own test run the
 * module is on the classpath, so without the {@code FilteredClassLoader} the
 * case "application without passkeys" would never be exercised -- and a bean
 * definition that mentions a missing type in its signature would stop the
 * application at startup, not at the first passkey.
 */
class IdentityBeansPasskeyTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(IdentityBeans.class)
            .withBean(IdentityProperties.class, IdentityProperties::defaults)
            .withBean(UserAccountRepository.class, () -> mock(UserAccountRepository.class))
            .withBean(RoleRepository.class, () -> mock(RoleRepository.class))
            .withBean(AuthTokenRepository.class, () -> mock(AuthTokenRepository.class))
            .withBean(PasskeyRepository.class, () -> mock(PasskeyRepository.class))
            .withBean(ExternalIdentityRepository.class, () -> mock(ExternalIdentityRepository.class))
            .withBean(IdentityMailSender.class, () -> mock(IdentityMailSender.class));

    @Test
    void withoutTheWebAuthnModuleTheBuildingBlockStillStarts() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(UserCredentialRepository.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PasskeyService.class);
                    assertThat(context).doesNotHaveBean("identityUserCredentialRepository");
                    assertThat(context).doesNotHaveBean("identityPasskeyUserEntityRepository");
                });
    }

    @Test
    void withTheModuleTheStoresForSpringSecurityAppear() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(UserCredentialRepository.class);
            assertThat(context).hasSingleBean(PublicKeyCredentialUserEntityRepository.class);
        });
    }

    /** As every other bean of the building block: an application's own displaces it. */
    @Test
    void anApplicationCanBringItsOwnStores() {
        UserCredentialRepository own = mock(UserCredentialRepository.class);

        contextRunner
                .withBean(UserCredentialRepository.class, () -> own)
                .run(context -> assertThat(context).getBean(UserCredentialRepository.class).isSameAs(own));
    }
}
