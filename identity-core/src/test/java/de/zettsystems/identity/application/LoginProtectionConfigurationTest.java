package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.MutableTestClock;
import de.zettsystems.identity.testsupport.RecordingMailSender;
import de.zettsystems.identity.values.IdentityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.TestingAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** When the protection against guessing takes over the password sign-in, and when it stays out of the way. */
class LoginProtectionConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Collaborators.class, IdentityBeans.LoginProtection.class);

    @Configuration(proxyBeanMethods = false)
    static class Collaborators {

        @Bean
        UserAccountRepository userRepository() {
            return mock(UserAccountRepository.class);
        }

        @Bean
        UserDetailsService userDetailsService() {
            return mock(UserDetailsService.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        IdentityMailSender mailSender() {
            return new RecordingMailSender();
        }

        @Bean
        Clock clock() {
            return new MutableTestClock();
        }

        @Bean
        IdentityProperties identityProperties() {
            return IdentityProperties.defaults();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnProvider {

        @Bean
        AuthenticationProvider ownProvider() {
            return new TestingAuthenticationProvider();
        }
    }

    @Test
    void onWithoutAnyConfiguration() {
        runner.run(context -> assertThat(context).getBean(AuthenticationProvider.class)
                .isInstanceOf(LoginProtectionAuthenticationProvider.class));
    }

    @Test
    void offWhenSwitchedOff() {
        runner.withPropertyValues("zs.identity.login-protection.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(AuthenticationProvider.class)
                        .doesNotHaveBean(LoginThrottle.class)
                        .doesNotHaveBean(LoginAttempts.class));
    }

    /** With two provider beans Spring Security would take neither; the application's stays the only one. */
    @Test
    void stepsBackForAProviderOfTheApplication() {
        new ApplicationContextRunner()
                .withUserConfiguration(Collaborators.class, OwnProvider.class, IdentityBeans.LoginProtection.class)
                .run(context -> assertThat(context).getBean(AuthenticationProvider.class)
                        .isInstanceOf(TestingAuthenticationProvider.class));
    }
}
