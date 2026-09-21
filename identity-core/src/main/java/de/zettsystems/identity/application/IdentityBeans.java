package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.PasskeyRepository;
import de.zettsystems.identity.domain.RoleRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.values.IdentityProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

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
                                          AuthenticationRefresher authenticationRefresher) {
        return new UserAccountServiceImpl(userRepository, roleRepository, passwordHasher,
                properties, clock, authenticationRefresher);
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
                                              IdentityProperties properties) {
        return new PasswordResetServiceImpl(userRepository, tokenIssuer, mailSender, passwordHasher, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    InvitationService invitationService(UserAccountRepository userRepository,
                                        RoleRepository roleRepository,
                                        AuthTokenIssuer tokenIssuer,
                                        IdentityMailSender mailSender,
                                        PasswordHasher passwordHasher,
                                        IdentityProperties properties,
                                        Clock clock) {
        return new InvitationServiceImpl(userRepository, roleRepository, tokenIssuer, mailSender,
                passwordHasher, properties, clock);
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
