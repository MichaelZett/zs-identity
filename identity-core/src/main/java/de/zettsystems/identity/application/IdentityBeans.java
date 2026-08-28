package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.RoleRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.values.IdentityProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.List;

/**
 * Alle Beans des Bausteins — explizit deklariert statt per Komponentensuche
 * eingesammelt.
 *
 * <p>Jede steht unter {@code @ConditionalOnMissingBean}: Definiert die Anwendung
 * eine eigene Bean desselben Typs, gewinnt ihre. Das ist der Unterschied
 * zwischen "der Baustein lässt sich anpassen" und "der Baustein diktiert" —
 * bei gescannten Komponenten gäbe es diese Wahl nicht.
 */
@Configuration(proxyBeanMethods = false)
public class IdentityBeans {

    /**
     * Als Bean und nicht als {@code Instant.now()} im Code: Tests können die Zeit
     * damit festhalten und den Ablauf von Token prüfen, ohne zu warten.
     */
    @Bean
    @ConditionalOnMissingBean
    Clock identityClock() {
        return Clock.systemUTC();
    }

    /** Texte des Bausteins; die Anwendung kann sie durch eine eigene Bean ersetzen. */
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

    /**
     * Hält die Berechtigungen der laufenden Sitzung aktuell, wenn sich die
     * Rollen des angemeldeten Kontos ändern.
     */
    @Bean
    @ConditionalOnMissingBean
    AuthenticationRefresher authenticationRefresher(UserDetailsService userDetailsService) {
        return new AuthenticationRefresher(userDetailsService);
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
    UserDetailsService identityUserDetailsService(UserAccountRepository userRepository) {
        return new IdentityUserDetailsService(userRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    LoginRecorder loginRecorder(UserAccountRepository userRepository, Clock clock) {
        return new LoginRecorder(userRepository, clock);
    }

    /** Basisrollen des Bausteins; die fachlichen kommen aus der Anwendung. */
    @Bean
    @ConditionalOnMissingBean(BuiltinRoleCatalog.class)
    RoleCatalog builtinRoleCatalog() {
        return new BuiltinRoleCatalog();
    }

    /**
     * Muss vor allem laufen, was Rollen erwartet — sonst findet die erste
     * Registrierung ihre Standardrolle nicht.
     */
    @Bean
    @ConditionalOnMissingBean
    @Order(0)
    RoleSynchronizer roleSynchronizer(List<RoleCatalog> catalogs, RoleRepository roleRepository) {
        return new RoleSynchronizer(catalogs, roleRepository);
    }
}
