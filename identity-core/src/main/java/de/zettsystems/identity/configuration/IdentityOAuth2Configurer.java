package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.ExternalSignInService;
import de.zettsystems.identity.values.IdentityPaths;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.OAuth2Settings;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.savedrequest.RequestCache;

/**
 * Sign-in through external identity providers (Google, GitHub, a company's
 * Keycloak or Entra ID) for an application's security filter chain, in one
 * line (since 1.2.0):
 *
 * <pre>{@code
 * http.with(IdentityOAuth2Configurer.oauth2Login(), Customizer.withDefaults());
 * }</pre>
 *
 * <p>Needs the OAuth2 client on the classpath
 * ({@code spring-boot-starter-security-oauth2-client}) and does nothing at
 * all while {@code zs.identity.oauth2.enabled} is {@code false}. The
 * providers are Spring Boot's
 * {@code spring.security.oauth2.client.registration.*}; the redirect URI to
 * register with a provider is {@code {baseUrl}/login/oauth2/code/{registrationId}}.
 *
 * <p>What it sets up, on top of Spring's own {@code http.oauth2Login(..)},
 * which it calls:
 *
 * <ul>
 *   <li>The access rules for {@link IdentityPaths#OAUTH2_AUTHORIZATION} and
 *       {@link IdentityPaths#OAUTH2_CALLBACK}, open to everyone, added where
 *       the {@code with(..)} call sits -- the same reasoning as for
 *       {@code IdentityPasskeyConfigurer}.</li>
 *   <li>The building block's sign-in page as the login page, so that Spring
 *       neither generates one of its own nor, with a single provider, sends
 *       every unauthenticated request straight to it.</li>
 *   <li>The account behind the provider's answer
 *       ({@code ExternalSignInService}): linked identity, join by verified
 *       address, invitation, or a new account. The session's principal is an
 *       {@code ExternalSignInUser} -- an {@code IdentityUserDetails} like after
 *       any other sign-in, and the {@code OAuth2User} Spring's token needs.</li>
 *   <li>Invitations redeemed and providers linked through the round trip
 *       ({@link IdentityPaths#INVITATION_PARAMETER},
 *       {@link IdentityPaths#LINK_PARAMETER}); linking only from a fresh
 *       sign-in.</li>
 *   <li>Where the browser goes afterwards: as after the form login when it
 *       worked, the sign-in page with the reason when it did not.</li>
 * </ul>
 *
 * <p>The filter Spring builds uses the chain's shared
 * {@code AuthenticationManager}, remember-me services, session strategy and
 * {@code SecurityContextRepository} by itself -- the gaps the passkey
 * configurer has to close do not exist here.
 */
public final class IdentityOAuth2Configurer extends AbstractHttpConfigurer<IdentityOAuth2Configurer, HttpSecurity> {

    /** Set in {@link #setBuilder} when the sign-in through providers is switched on. */
    private @Nullable ExternalSignInHandlers handlers;

    private IdentityOAuth2Configurer() {
        // through oauth2Login()
    }

    public static IdentityOAuth2Configurer oauth2Login() {
        return new IdentityOAuth2Configurer();
    }

    /**
     * Called by {@code http.with(..)} the moment the configurer is handed
     * over; the access rules go in here for the reason given on
     * {@code IdentityPasskeyConfigurer#setBuilder}.
     */
    @Override
    public void setBuilder(HttpSecurity http) {
        super.setBuilder(http);
        ApplicationContext context = http.getSharedObject(ApplicationContext.class);
        OAuth2Settings settings = context.getBean(IdentityProperties.class).oauth2();
        if (!settings.enabled()) {
            return;
        }
        ClientRegistrationRepository registrations =
                context.getBeanProvider(ClientRegistrationRepository.class).getIfAvailable();
        if (registrations == null) {
            throw new IllegalStateException("zs.identity.oauth2.enabled is true, but no provider is configured: "
                    + "add one under spring.security.oauth2.client.registration.<id>");
        }
        ExternalSignInService signInService = context.getBean(ExternalSignInService.class);
        ExternalClaimsReader claimsReader =
                context.getBeanProvider(ExternalClaimsReader.class).getIfAvailable(ExternalClaimsReader::standard);
        SecurityContextHolderStrategy strategy = getSecurityContextHolderStrategy();

        ExternalSignInHandlers signInHandlers = new ExternalSignInHandlers();
        this.handlers = signInHandlers;
        ExternalAuthorizationRequestResolver resolver = new ExternalAuthorizationRequestResolver(
                new DefaultOAuth2AuthorizationRequestResolver(registrations, IdentityPaths.OAUTH2_AUTHORIZATION),
                strategy);
        ExternalSignInConverter converter = new ExternalSignInConverter(claimsReader, signInService, strategy);

        http.oauth2Login(login -> login
                .loginPage("/" + IdentityPaths.LOGIN)
                .clientRegistrationRepository(registrations)
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                .successHandler(signInHandlers.success())
                .failureHandler(signInHandlers.failure())
                .withObjectPostProcessor(new ObjectPostProcessor<OAuth2LoginAuthenticationFilter>() {
                    @Override
                    public <O extends OAuth2LoginAuthenticationFilter> O postProcess(O filter) {
                        filter.setAuthenticationResultConverter(converter);
                        return filter;
                    }
                }));
        http.authorizeHttpRequests(registry -> registry
                .requestMatchers(IdentityPaths.OAUTH2_AUTHORIZATION + "/*", IdentityPaths.OAUTH2_CALLBACK)
                .permitAll());
    }

    /** The request cache exists once every configurer has run its {@code init}. */
    @Override
    public void configure(HttpSecurity http) {
        ExternalSignInHandlers signInHandlers = handlers;
        if (signInHandlers == null) {
            return;
        }
        RequestCache requestCache = http.getSharedObject(RequestCache.class);
        if (requestCache != null) {
            signInHandlers.useRequestCache(requestCache);
        }
    }
}
