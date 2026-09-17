package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.IdentityMailFactory;
import de.zettsystems.identity.application.IdentityMailSender;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.JavaMailFactory;
import de.zettsystems.identity.values.IdentityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Wählt den Mailversand des Bausteins.
 *
 * <p>Bewusst eine eigene Auto-Konfiguration mit {@code after =
 * MailSenderAutoConfiguration} und nicht einfach eine Bean in
 * {@code IdentityBeans}: {@code @ConditionalOnBean} entscheidet anhand dessen,
 * was zum Zeitpunkt der Auswertung <em>schon</em> registriert ist. In einer per
 * {@code @Import} eingebundenen {@code @Configuration} läuft die Prüfung, bevor
 * Spring Boot den {@code JavaMailSender} angelegt hat — die Bedingung ist dann
 * immer falsch, und die Anwendung startet mit "required a bean of type
 * IdentityMailSender that could not be found" gar nicht erst.
 *
 * <p><strong>Zweigeteilt, weil {@code spring-boot-starter-mail} seit 0.7.0
 * optional ist.</strong> Alles, was eine Mail-Bibliothek anfasst, steckt in
 * {@link JavaMail} unter {@code @ConditionalOnClass} — ohne die Bibliothek
 * wird diese Klasse nie geladen. Die Rückfallebene hier draußen kommt ohne sie
 * aus und prüft deshalb auf den <em>Namen</em> statt auf die Klasse: Ein
 * Klassenliteral in {@code @ConditionalOnMissingBean} läse Spring zwar per ASM
 * aus dem Bytecode, aber der Name sagt hier klarer, was gemeint ist.
 *
 * <p>Der Verweis auf {@code MailSenderAutoConfiguration} steht aus demselben
 * Grund als Zeichenkette ({@code afterName}).
 */
@AutoConfiguration(afterName = "org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration")
public class IdentityMailAutoConfiguration {

    /**
     * Rückfallebene ohne konfigurierten Mailversand: schreibt die Links ins Log,
     * damit die Anwendung wenigstens startet und benutzbar bleibt. Greift auch
     * dann, wenn die Anwendung die Mail-Bibliothek gar nicht mitbringt.
     */
    @Bean
    @ConditionalOnMissingBean(value = IdentityMailSender.class,
            type = "org.springframework.mail.javamail.JavaMailSender")
    IdentityMailSender loggingIdentityMailSender() {
        return IdentityMailFactory.logOnly();
    }

    /** Der Normalfall: Die Anwendung bringt die Mail-Bibliothek mit und hat {@code spring.mail.*} gesetzt. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JavaMailSender.class)
    static class JavaMail {

        @Bean
        @ConditionalOnBean(JavaMailSender.class)
        @ConditionalOnMissingBean(IdentityMailSender.class)
        IdentityMailSender javaMailIdentityMailSender(JavaMailSender mailSender, IdentityProperties properties,
                                                      IdentityMessages messages) {
            return JavaMailFactory.javaMail(mailSender, properties, messages);
        }
    }
}
