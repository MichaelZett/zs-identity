package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.ExternalIdentityRepository;
import de.zettsystems.identity.domain.PasskeyRepository;
import de.zettsystems.identity.domain.RoleRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.values.IdentityProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.util.ClassUtils;

import java.time.Clock;
import java.util.List;

/**
 * Every bean of this building block, declared explicitly instead of collected
 * by a component scan.
 *
 * <p>Each one sits under {@code @ConditionalOnMissingBean}: if the application
 * defines a bean of the same type, that one wins. This is the difference
 * between "the building block can be adapted" and "the building block
 * dictates" -- with scanned components there would be no such choice.
 */
@Configuration(proxyBeanMethods = false)
public class IdentityBeans {

    /**
     * A bean instead of {@code Instant.now()} in the code: this lets tests hold
     * time still and check that tokens expire, without waiting.
     */
    @Bean
    @ConditionalOnMissingBean
    Clock identityClock() {
        return Clock.systemUTC();
    }

    /** Texts of the building block; the application can replace them with its own bean. */
    @Bean
    @ConditionalOnMissingBean
    IdentityMessages identityMessages() {
        return IdentityMessages.resourceBundles();
    }

    @Bean
    @ConditionalOnMissingBean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean
    PasswordHasher passwordHasher(PasswordEncoder passwordEncoder) {
        return new PasswordHasher(passwordEncoder);
    }

    @Bean
    @ConditionalOnMissingBean
    AuthTokenIssuer authTokenIssuer(AuthTokenRepository tokenRepository,
                                    IdentityProperties properties,
                                    Clock clock) {
        return new AuthTokenIssuer(tokenRepository, properties, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    UserAccountService userAccountService(UserAccountRepository userRepository,
                                          RoleRepository roleRepository,
                                          PasswordHasher passwordHasher,
                                          IdentityProperties properties,
                                          Clock clock,
                                          AuthenticationRefresher authenticationRefresher,
                                          ApplicationEventPublisher events) {
        return new UserAccountServiceImpl(userRepository, roleRepository, passwordHasher,
                properties, clock, authenticationRefresher, events);
    }

    /** The daily token cleanup run; can be switched off through {@code zs.identity.token-cleanup.enabled}. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "zs.identity.token-cleanup.enabled", havingValue = "true", matchIfMissing = true)
    TokenCleanupScheduler tokenCleanupScheduler(AuthTokenRepository tokenRepository, Clock clock) {
        return new TokenCleanupScheduler(tokenRepository, clock);
    }

    /**
     * Keeps the authorities of the running session up to date when the roles of
     * the signed-in account change.
     */
    @Bean
    @ConditionalOnMissingBean
    AuthenticationRefresher authenticationRefresher(UserDetailsService userDetailsService) {
        return new AuthenticationRefresher(userDetailsService);
    }

    /**
     * Switching the active scope. Unused in an application without tenants; the
     * bean costs nothing and saves the first multi-tenant application the
     * question of where to get it from.
     */
    @Bean
    @ConditionalOnMissingBean
    ActiveScopeService activeScopeService() {
        return new ActiveScopeService();
    }

    @Bean
    @ConditionalOnMissingBean
    RegistrationService registrationService(UserAccountRepository userRepository,
                                            RoleRepository roleRepository,
                                            AuthTokenIssuer tokenIssuer,
                                            IdentityMailSender mailSender,
                                            PasswordHasher passwordHasher,
                                            IdentityProperties properties,
                                            Clock clock) {
        return new RegistrationServiceImpl(userRepository, roleRepository, tokenIssuer, mailSender,
                passwordHasher, properties, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    PasswordResetService passwordResetService(UserAccountRepository userRepository,
                                              AuthTokenIssuer tokenIssuer,
                                              IdentityMailSender mailSender,
                                              PasswordHasher passwordHasher,
                                              IdentityProperties properties,
                                              ApplicationEventPublisher events) {
        return new PasswordResetServiceImpl(userRepository, tokenIssuer, mailSender, passwordHasher,
                properties, events);
    }

    @Bean
    @ConditionalOnMissingBean
    InvitationService invitationService(UserAccountRepository userRepository,
                                        RoleRepository roleRepository,
                                        AuthTokenIssuer tokenIssuer,
                                        IdentityMailSender mailSender,
                                        PasswordHasher passwordHasher,
                                        IdentityProperties properties,
                                        Clock clock,
                                        ApplicationEventPublisher events) {
        return new InvitationServiceImpl(userRepository, roleRepository, tokenIssuer, mailSender,
                passwordHasher, properties, clock, events);
    }

    /**
     * Throws the remember-me tokens of an account out when its password, its
     * address or its state changes. Does nothing in an application without
     * remember-me -- the repository is optional -- and can be switched off
     * entirely with {@code zs.identity.remember-me-cleanup.enabled=false} by an
     * application that would rather act on the events itself.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "zs.identity.remember-me-cleanup.enabled", havingValue = "true",
            matchIfMissing = true)
    RememberMeTokenCleaner rememberMeTokenCleaner(ObjectProvider<PersistentTokenRepository> tokenRepository) {
        return new RememberMeTokenCleaner(tokenRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    UserDetailsService identityUserDetailsService(UserAccountRepository userRepository) {
        return new IdentityUserDetailsService(userRepository);
    }

    /**
     * What an application shows about passkeys. Free of WebAuthn types, so
     * it exists whether or not the application brings the module.
     */
    @Bean
    @ConditionalOnMissingBean
    PasskeyService passkeyService(PasskeyRepository passkeyRepository) {
        return new PasskeyServiceImpl(passkeyRepository);
    }

    /**
     * The sign-in through external providers (since 1.2.0). Free of OAuth2
     * types, so it exists whether or not the application brings the client;
     * {@code IdentityOAuth2Configurer} calls it once the provider's answer is
     * checked.
     */
    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("java:S107") // one collaborator per concern, as in the other services
    ExternalSignInService externalSignInService(UserAccountRepository userRepository,
                                                ExternalIdentityRepository identityRepository,
                                                RoleRepository roleRepository,
                                                AuthTokenIssuer tokenIssuer,
                                                AuthTokenRepository tokenRepository,
                                                IdentityProperties properties,
                                                Clock clock,
                                                ApplicationEventPublisher events,
                                                ObjectProvider<LoginThrottle> throttle) {
        return new ExternalSignInServiceImpl(userRepository, identityRepository, roleRepository, tokenIssuer,
                tokenRepository, properties, clock, events, throttle);
    }

    /** What an application shows about linked providers; see {@link #passkeyService}. */
    @Bean
    @ConditionalOnMissingBean
    ExternalIdentityService externalIdentityService(UserAccountRepository userRepository,
                                                    ExternalIdentityRepository identityRepository,
                                                    PasskeyRepository passkeyRepository,
                                                    ApplicationEventPublisher events) {
        return new ExternalIdentityServiceImpl(userRepository, identityRepository, passkeyRepository, events);
    }

    /**
     * The providers the sign-in page offers. The class that reads Spring's
     * client registrations is only loaded when the OAuth2 client is on the
     * classpath and the sign-in through providers is switched on; otherwise
     * there are none, and the page shows no button.
     */
    @Bean
    @ConditionalOnMissingBean
    ExternalProviders externalProviders(IdentityProperties properties, ApplicationContext context) {
        if (!properties.oauth2().enabled()
                || !ClassUtils.isPresent(CLIENT_REGISTRATION_REPOSITORY, context.getClassLoader())) {
            return ExternalProviders.none();
        }
        return new ClientRegistrationProviders(properties.oauth2(), context);
    }

    private static final String CLIENT_REGISTRATION_REPOSITORY =
            "org.springframework.security.oauth2.client.registration.ClientRegistrationRepository";

    /**
     * The stores Spring Security's WebAuthn filters read and write, backed by
     * the building block's tables. Only with {@code spring-security-webauthn}
     * on the classpath: the interfaces come from there, and a class that
     * mentions them in a signature cannot even be loaded without it.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(UserCredentialRepository.class)
    static class Passkeys {

        @Bean
        @ConditionalOnMissingBean
        UserCredentialRepository identityUserCredentialRepository(PasskeyRepository passkeyRepository,
                                                                  UserAccountRepository userRepository,
                                                                  Clock clock) {
            return new JpaUserCredentialRepository(passkeyRepository, userRepository, clock);
        }

        @Bean
        @ConditionalOnMissingBean
        PublicKeyCredentialUserEntityRepository identityPasskeyUserEntityRepository(
                UserAccountRepository userRepository) {
            return new JpaPublicKeyCredentialUserEntityRepository(userRepository);
        }
    }

    /**
     * The protection against password guessing (see
     * {@link LoginProtectionAuthenticationProvider}), on unless
     * {@code zs.identity.login-protection.enabled=false}.
     *
     * <p>The provider steps back when the application declares an
     * {@link AuthenticationProvider} of its own: Spring Security takes a
     * provider bean only when there is exactly one, and with two it would
     * quietly take neither -- which would change the sign-in of an
     * application that worked before. Such an application wires the
     * protection into its own provider or goes without it.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "zs.identity.login-protection.enabled", havingValue = "true",
            matchIfMissing = true)
    static class LoginProtection {

        @Bean
        @ConditionalOnMissingBean
        LoginThrottle loginThrottle(IdentityProperties properties, Clock clock) {
            return new LoginThrottle(properties.loginProtection(), clock);
        }

        @Bean
        @ConditionalOnMissingBean
        LoginAttempts loginAttempts(UserAccountRepository userRepository, IdentityProperties properties,
                                    IdentityMailSender mailSender, ApplicationEventPublisher events, Clock clock) {
            return new LoginAttempts(userRepository, properties, mailSender, events, clock,
                    command -> Thread.ofVirtual().name("identity-lock-notice").start(command));
        }

        @Bean
        @ConditionalOnMissingBean(AuthenticationProvider.class)
        LoginProtectionAuthenticationProvider loginProtectionAuthenticationProvider(
                UserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
                LoginThrottle throttle, LoginAttempts attempts) {
            DaoAuthenticationProvider passwordCheck = new DaoAuthenticationProvider(
                    passwordAccountsOnly(userDetailsService));
            passwordCheck.setPasswordEncoder(passwordEncoder);
            return new LoginProtectionAuthenticationProvider(passwordCheck, throttle, attempts);
        }
    }

    /**
     * The accounts the password form may check: those with a password. An
     * account that signs in through an external provider only is turned
     * down as if its address were unknown -- the
     * {@code DaoAuthenticationProvider} then compares against its dummy hash,
     * so the answer takes as long as for a wrong password, and whoever is
     * guessing learns nothing about which addresses have an account.
     */
    static UserDetailsService passwordAccountsOnly(UserDetailsService userDetailsService) {
        return username -> {
            UserDetails user = userDetailsService.loadUserByUsername(username);
            if (user.getPassword() == null) {
                throw new UsernameNotFoundException("No password for " + username);
            }
            return user;
        };
    }

    @Bean
    @ConditionalOnMissingBean
    LoginRecorder loginRecorder(UserAccountRepository userRepository, Clock clock) {
        return new LoginRecorder(userRepository, clock);
    }

    /** Base roles of the building block; the domain roles come from the application. */
    @Bean
    @ConditionalOnMissingBean(BuiltinRoleCatalog.class)
    RoleCatalog builtinRoleCatalog() {
        return new BuiltinRoleCatalog();
    }

    /**
     * Has to run before anything that expects roles, otherwise the first
     * registration does not find its default role.
     */
    @Bean
    @ConditionalOnMissingBean
    @Order(0)
    RoleSynchronizer roleSynchronizer(List<RoleCatalog> catalogs, RoleRepository roleRepository) {
        return new RoleSynchronizer(catalogs, roleRepository);
    }
}
