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
 * Der Mailversand ist seit 0.7.0 optional: {@code spring-boot-starter-mail}
 * hängt nur noch {@code compileOnly} am Baustein.
 *
 * <p>Dieser Test ist der Beweis dafür, dass das auch trägt. Ohne ihn wäre es
 * eine Behauptung — im eigenen Testlauf liegt die Mail-Bibliothek ja auf dem
 * Klassenpfad, und der Fall „Anwendung ohne Mailversand" käme nie vor. Der
 * {@code FilteredClassLoader} blendet sie gezielt aus.
 */
class IdentityMailAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdentityMailAutoConfiguration.class))
            .withBean(IdentityMessages.class, IdentityMessages::resourceBundles)
            .withBean(IdentityProperties.class, IdentityProperties::defaults);

    /**
     * Der Fall, für den die Entkopplung gemacht ist: eine REST-Anwendung ohne
     * jede Mail-Bibliothek. Sie muss starten — mit dem Log-Versand.
     */
    @Test
    void withoutTheMailLibraryTheModuleFallsBackToTheLog() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(JavaMailSender.class))
                .run(context -> assertThat(context)
                        .as("ohne Rückfallebene bliebe die Anwendung mit einer fehlenden Bean stehen")
                        .hasSingleBean(IdentityMailSender.class));
    }

    /** Mail-Bibliothek da, aber kein {@code spring.mail.*} konfiguriert: ebenfalls Log-Versand. */
    @Test
    void withTheLibraryButWithoutAConfiguredSenderTheLogStaysTheFallback() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(IdentityMailSender.class));
    }

    /** Der Normalfall: Die Anwendung hat einen {@code JavaMailSender}. */
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

    /** Eine eigene Bean der Anwendung verdrängt beide Varianten. */
    @Test
    void anApplicationCanBringItsOwnSender() {
        IdentityMailSender own = new IdentityMailSender() {
            @Override
            public void sendEmailVerification(de.zettsystems.identity.values.UserAccountDto user, String url) {
                // Testdoppel
            }

            @Override
            public void sendPasswordReset(de.zettsystems.identity.values.UserAccountDto user, String url) {
                // Testdoppel
            }
        };

        contextRunner
                .withBean(IdentityMailSender.class, () -> own)
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .run(context -> assertThat(context).getBean(IdentityMailSender.class).isSameAs(own));
    }
}
