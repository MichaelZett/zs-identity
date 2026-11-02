package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.IdentityMailHealthIndicator;
import de.zettsystems.identity.application.IdentityMailSender;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.MailDeliveryListener;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.UserAccountDto;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.mail.autoconfigure.MailHealthContributorAutoConfiguration;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The health component is an offer, not a change: an application that does
 * nothing keeps its health endpoint exactly as it was. The tests that hide a
 * library prove that the feature needs neither of them, as in
 * {@link IdentityMailAutoConfigurationTest}.
 */
class IdentityMailHealthAutoConfigurationTest {

    private static final String ON = "zs.identity.mail-health.enabled=true";
    private static final String MAIL_HOST = "spring.mail.host=localhost";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class,
                    IdentityMailAutoConfiguration.class, IdentityMailHealthAutoConfiguration.class,
                    MailHealthContributorAutoConfiguration.class))
            .withBean(IdentityMessages.class, IdentityMessages::resourceBundles)
            .withBean(IdentityProperties.class, IdentityProperties::defaults);

    @Test
    void doingNothingChangesNothing() {
        contextRunner.withPropertyValues(MAIL_HOST).run(context -> {
            assertThat(context).doesNotHaveBean(IdentityMailHealthIndicator.class);
            assertThat(context).hasBean("mailHealthContributor");
        });
    }

    @Test
    void switchingItOnIsEnoughToReplaceSpringBootsIndicator() {
        contextRunner.withPropertyValues(MAIL_HOST, ON).run(context -> {
            assertThat(context).hasSingleBean(IdentityMailHealthIndicator.class);
            assertThat(context).hasBean("mailHealthIndicator");
            assertThat(context).doesNotHaveBean("mailHealthContributor");
        });
    }

    @Test
    void withoutAMailServerThereIsNothingToCheck() {
        contextRunner.withPropertyValues(ON)
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(IdentityMailHealthIndicator.class));
    }

    @Test
    void anApplicationWithoutTheHealthLibraryStillStarts() {
        contextRunner.withPropertyValues(MAIL_HOST, ON)
                .withClassLoader(new FilteredClassLoader(HealthIndicator.class))
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(IdentityMailHealthIndicator.class));
    }

    @Test
    void anApplicationWithoutTheMailLibraryStillStarts() {
        contextRunner.withPropertyValues(ON)
                .withClassLoader(new FilteredClassLoader(JavaMailSender.class))
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(IdentityMailHealthIndicator.class));
    }

    @Test
    void aListenerOfTheApplicationHearsOfDeliveries() {
        MailDeliveryListener listener = mock(MailDeliveryListener.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        contextRunner
                .withBean(MailDeliveryListener.class, () -> listener)
                .withBean(JavaMailSender.class, () -> sender)
                .run(context -> {
                    context.getBean(IdentityMailSender.class).sendPasswordReset(someUser(), "http://example.com/r");
                    verify(listener).delivered();
                });
    }

    private static UserAccountDto someUser() {
        return new UserAccountDto(1L, "anna@example.com", AccountName.of("Anna", "Beispiel"), true, true,
                Instant.parse("2026-09-01T10:00:00Z"), Set.of("USER"));
    }
}
