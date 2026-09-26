package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.ExternalIdentityRepository;
import de.zettsystems.identity.domain.PasskeyRepository;
import de.zettsystems.identity.domain.RoleRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.values.ExternalProvider;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.OAuth2Settings;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The OAuth2 client is optional (since 1.2.0), like the WebAuthn module:
 * without it the building block starts, and the sign-in page asks for
 * providers all the same and finds none.
 */
class IdentityBeansOAuth2Test {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(IdentityBeans.class)
            .withBean(UserAccountRepository.class, () -> mock(UserAccountRepository.class))
            .withBean(RoleRepository.class, () -> mock(RoleRepository.class))
            .withBean(AuthTokenRepository.class, () -> mock(AuthTokenRepository.class))
            .withBean(PasskeyRepository.class, () -> mock(PasskeyRepository.class))
            .withBean(ExternalIdentityRepository.class, () -> mock(ExternalIdentityRepository.class))
            .withBean(IdentityMailSender.class, () -> mock(IdentityMailSender.class));

    @Test
    void withoutTheOAuth2ClientTheBuildingBlockStartsAndOffersNothing() {
        contextRunner
                .withBean(IdentityProperties.class, () -> switchedOn(List.of()))
                .withClassLoader(new FilteredClassLoader(ClientRegistrationRepository.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ExternalSignInService.class);
                    assertThat(context).hasSingleBean(ExternalIdentityService.class);
                    assertThat(context.getBean(ExternalProviders.class).offered()).isEmpty();
                });
    }

    @Test
    void switchedOffNothingIsOffered() {
        contextRunner
                .withBean(IdentityProperties.class, IdentityProperties::defaults)
                .withBean(ClientRegistrationRepository.class, () -> registrations())
                .run(context -> assertThat(context.getBean(ExternalProviders.class).offered()).isEmpty());
    }

    /** Spring keeps them in a hash map; by name, so the buttons stay where they are. */
    @Test
    void everyRegistrationIsOfferedByNameWhenNoneAreNamed() {
        contextRunner
                .withBean(IdentityProperties.class, () -> switchedOn(List.of()))
                .withBean(ClientRegistrationRepository.class, () -> registrations())
                .run(context -> assertThat(context.getBean(ExternalProviders.class).offered())
                        .containsExactly(new ExternalProvider("github", "github"),
                                new ExternalProvider("google", "Google")));
    }

    /** In the configured order, and a name that is not configured leads to no button. */
    @Test
    void theNamedRegistrationsAreOfferedInTheirOrder() {
        contextRunner
                .withBean(IdentityProperties.class, () -> switchedOn(List.of("github", "nowhere", "google")))
                .withBean(ClientRegistrationRepository.class, () -> registrations())
                .run(context -> assertThat(context.getBean(ExternalProviders.class).offered())
                        .extracting(ExternalProvider::registrationId)
                        .containsExactly("github", "google"));
    }

    @Test
    void switchedOnWithoutAnyRegistrationNothingIsOffered() {
        contextRunner
                .withBean(IdentityProperties.class, () -> switchedOn(List.of()))
                .run(context -> assertThat(context.getBean(ExternalProviders.class).offered()).isEmpty());
    }

    private static IdentityProperties switchedOn(List<String> registrations) {
        return IdentityProperties.defaults()
                .withOAuth2(OAuth2Settings.defaults().enabled(true).registrations(registrations));
    }

    private static ClientRegistrationRepository registrations() {
        return new InMemoryClientRegistrationRepository(registration("google", "Google"),
                registration("github", null));
    }

    private static ClientRegistration registration(String id, String name) {
        ClientRegistration.Builder builder = ClientRegistration.withRegistrationId(id)
                .clientId("client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("http://localhost/authorize")
                .tokenUri("http://localhost/token");
        if (name != null) {
            builder.clientName(name);
        }
        return builder.build();
    }
}
