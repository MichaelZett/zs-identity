package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.IdentityMailFactory;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.IdentityMailSender;
import de.zettsystems.identity.values.IdentityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Wählt den Mailversand des Bausteins.
 *
 * <p>Bewusst eine eigene Auto-Konfiguration mit {@code after =
 * MailSenderAutoConfiguration.class} und nicht einfach eine Bean in
 * {@code IdentityBeans}: {@code @ConditionalOnBean} entscheidet anhand dessen,
 * was zum Zeitpunkt der Auswertung <em>schon</em> registriert ist. In einer per
 * {@code @Import} eingebundenen {@code @Configuration} läuft die Prüfung, bevor
 * Spring Boot den {@code JavaMailSender} angelegt hat — die Bedingung ist dann
 * immer falsch, und die Anwendung startet mit "required a bean of type
 * IdentityMailSender that could not be found" gar nicht erst.
 */
@AutoConfiguration(after = MailSenderAutoConfiguration.class)
public class IdentityMailAutoConfiguration {

    /** Der Normalfall: Die Anwendung hat {@code spring.mail.*} konfiguriert. */
    @Bean
    @ConditionalOnBean(JavaMailSender.class)
    @ConditionalOnMissingBean(IdentityMailSender.class)
    IdentityMailSender javaMailIdentityMailSender(JavaMailSender mailSender, IdentityProperties properties,
                                                  IdentityMessages messages) {
        return IdentityMailFactory.javaMail(mailSender, properties, messages);
    }

    /**
     * Rückfallebene ohne konfigurierten Mailversand: schreibt die Links ins Log,
     * damit die Anwendung wenigstens startet und benutzbar bleibt.
     */
    @Bean
    @ConditionalOnMissingBean({IdentityMailSender.class, JavaMailSender.class})
    IdentityMailSender loggingIdentityMailSender() {
        return IdentityMailFactory.logOnly();
    }
}
