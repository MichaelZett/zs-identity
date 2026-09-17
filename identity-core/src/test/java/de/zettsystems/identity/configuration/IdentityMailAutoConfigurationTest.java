package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.IdentityMailSender;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.values.IdentityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Mail delivery has been optional since 0.7.0:
 * {@code spring-boot-starter-mail} only hangs {@code compileOnly} off the
 * building block.
 *
 * <p>This test is the proof that this actually holds. Without it, it would be a
 * claim: in our own test run the mail library is on the classpath after all,
 * and the case "application without mail delivery" would never occur. The
 * {@code FilteredClassLoader} hides it deliberately.
 */
class IdentityMailAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdentityMailAutoConfiguration.class))
            .withBean(IdentityMessages.class, IdentityMessages::resourceBundles)
            .withBean(IdentityProperties.class, IdentityProperties::defaults);

    /**
     * The case the decoupling was made for: a REST application without any mail
     * library at all. It has to start, with delivery to the log.
     */
    @Test
    void withoutTheMailLibraryTheModuleFallsBackToTheLog() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(JavaMailSender.class))
                .run(context -> assertThat(context)
                        .as("without the fallback the application would stop with a missing bean")
                        .hasSingleBean(IdentityMailSender.class));
    }

    /** Mail library present but no {@code spring.mail.*} configured: delivery to the log as well. */
    @Test
    void withTheLibraryButWithoutAConfiguredSenderTheLogStaysTheFallback() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(IdentityMailSender.class));
    }

    /** The normal case: the application has a {@code JavaMailSender}. */
    @Test
    void withAJavaMailSenderTheMailsGoOut() {
        contextRunner
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .run(context -> assertThat(context)
                        .hasSingleBean(IdentityMailSender.class)
                        .getBean(IdentityMailSender.class)
                        .satisfies(sender -> assertThat(sender.getClass().getSimpleName())
                                .isEqualTo("JavaMailIdentityMailSender")));
    }

    /** A bean of the application's own displaces both variants. */
    @Test
    void anApplicationCanBringItsOwnSender() {
        IdentityMailSender own = new IdentityMailSender() {
            @Override
            public void sendEmailVerification(de.zettsystems.identity.values.UserAccountDto user, String url) {
                // Test double
            }

            @Override
            public void sendPasswordReset(de.zettsystems.identity.values.UserAccountDto user, String url) {
                // Test double
            }
        };

        contextRunner
                .withBean(IdentityMailSender.class, () -> own)
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .run(context -> assertThat(context).getBean(IdentityMailSender.class).isSameAs(own));
    }
}
