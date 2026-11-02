package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.IdentityMailHealthIndicator;
import de.zettsystems.identity.values.MailHealthProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * The optional health component {@code mail}, off unless the application sets
 * {@code zs.identity.mail-health.enabled=true}.
 *
 * <p>The bean is called {@code mailHealthIndicator} because Spring Boot's own
 * contributor steps back from that name, so switching this on is enough and
 * nothing has to be switched off. This has to run before that
 * auto-configuration; the references are strings because both libraries are
 * optional here.
 *
 * <p>Without a {@code JavaMailSenderImpl} there is nothing to log in with:
 * the component stays away, and an application delivering through a transport
 * of its own reports through {@code MailDeliveryListener} if it wants to.
 */
@AutoConfiguration(
        afterName = "org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration",
        beforeName = "org.springframework.boot.mail.autoconfigure.MailHealthContributorAutoConfiguration")
@ConditionalOnClass(name = {
        "org.springframework.boot.health.contributor.HealthIndicator",
        "org.springframework.mail.javamail.JavaMailSenderImpl"})
@ConditionalOnProperty(prefix = "zs.identity.mail-health", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MailHealthProperties.class)
public class IdentityMailHealthAutoConfiguration {

    @Bean
    @ConditionalOnBean(JavaMailSenderImpl.class)
    @ConditionalOnMissingBean(name = "mailHealthIndicator")
    IdentityMailHealthIndicator mailHealthIndicator(JavaMailSenderImpl sender, MailHealthProperties properties) {
        return IdentityMailHealthIndicator.of(sender, properties.interval());
    }
}
