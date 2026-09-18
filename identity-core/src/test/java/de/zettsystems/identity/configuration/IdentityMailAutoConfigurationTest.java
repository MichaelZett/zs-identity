package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.IdentityMailSender;
import de.zettsystems.identity.application.IdentityMailTransport;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.IdentityMail;
import de.zettsystems.identity.values.IdentityMailType;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.UserAccountDto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
                .run(context -> {
                    assertThat(context).hasSingleBean(IdentityMailSender.class);
                    assertThat(context).getBean(IdentityMailTransport.class)
                            .satisfies(transport -> assertThat(transport.getClass().getSimpleName())
                                    .isEqualTo("JavaMailTransport"));
                });
    }

    /**
     * The seam of 0.9.0: an application that only wants to deliver differently
     * brings a transport and keeps the texts of the building block.
     */
    @Test
    void anApplicationCanBringItsOwnTransportAndKeepTheTexts() {
        List<IdentityMail> delivered = new ArrayList<>();
        IdentityMailTransport own = (mail, user) -> delivered.add(mail);

        contextRunner
                .withBean(IdentityMailTransport.class, () -> own)
                .withClassLoader(new FilteredClassLoader(JavaMailSender.class))
                .run(context -> {
                    assertThat(context).getBean(IdentityMailTransport.class).isSameAs(own);
                    context.getBean(IdentityMailSender.class)
                            .sendPasswordReset(someUser(), "http://example.com/reset?token=t");
                    assertThat(delivered).singleElement().satisfies(mail -> {
                        assertThat(mail.type()).isEqualTo(IdentityMailType.PASSWORD_RESET);
                        assertThat(mail.text()).contains("http://example.com/reset?token=t");
                    });
                });
    }

    /** An own transport wins over a configured {@code JavaMailSender}. */
    @Test
    void anOwnTransportDisplacesTheJavaMailOne() {
        IdentityMailTransport own = (mail, user) -> { };

        contextRunner
                .withBean(IdentityMailTransport.class, () -> own)
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .run(context -> assertThat(context).getBean(IdentityMailTransport.class).isSameAs(own));
    }

    private static UserAccountDto someUser() {
        return new UserAccountDto(1L, "anna@example.com", AccountName.of("Anna", "Beispiel"), true, true,
                Instant.parse("2026-09-01T10:00:00Z"), Set.of("USER"));
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
