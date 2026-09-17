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
 * Picks the mail delivery of the building block.
 *
 * <p>Deliberately an auto-configuration of its own with {@code after =
 * MailSenderAutoConfiguration} rather than simply a bean in
 * {@code IdentityBeans}: {@code @ConditionalOnBean} decides from what is
 * <em>already</em> registered at the time it is evaluated. Inside a
 * {@code @Configuration} pulled in through {@code @Import}, that check runs
 * before Spring Boot has created the {@code JavaMailSender}, so the condition
 * is always false and the application does not even start, failing with
 * "required a bean of type IdentityMailSender that could not be found".
 *
 * <p><strong>Split in two because {@code spring-boot-starter-mail} is optional
 * as of 0.7.0.</strong> Everything that touches a mail library sits in
 * {@link JavaMail} under {@code @ConditionalOnClass}; without the library that
 * class is never loaded. The fallback out here does without it and therefore
 * checks the <em>name</em> instead of the class: Spring would read a class
 * literal in {@code @ConditionalOnMissingBean} from the bytecode through ASM,
 * but the name says more clearly what is meant.
 *
 * <p>The reference to {@code MailSenderAutoConfiguration} is a string for the
 * same reason ({@code afterName}).
 */
@AutoConfiguration(afterName = "org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration")
public class IdentityMailAutoConfiguration {

    /**
     * Fallback without configured mail delivery: writes the links to the log,
     * so that the application at least starts and stays usable. It applies as
     * well when the application does not bring the mail library at all.
     */
    @Bean
    @ConditionalOnMissingBean(value = IdentityMailSender.class,
            type = "org.springframework.mail.javamail.JavaMailSender")
    IdentityMailSender loggingIdentityMailSender() {
        return IdentityMailFactory.logOnly();
    }

    /** The normal case: the application brings the mail library and has set {@code spring.mail.*}. */
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
