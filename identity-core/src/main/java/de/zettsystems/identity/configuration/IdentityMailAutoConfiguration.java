package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.IdentityMailFactory;
import de.zettsystems.identity.application.IdentityMailSender;
import de.zettsystems.identity.application.IdentityMailTransport;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.JavaMailFactory;
import de.zettsystems.identity.values.IdentityProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Picks the mail delivery of the building block: a transport, and the sender
 * that renders and hands over to it.
 *
 * <p>Two seams, coarse and fine. An application's own {@link IdentityMailSender}
 * bean replaces texts and delivery together; an own
 * {@link IdentityMailTransport} bean replaces delivery alone and keeps the
 * texts of the building block -- the seam for sending through a mail account
 * that depends on the recipient (since 0.9.0). Without either the transport
 * is {@code JavaMailSender} with the sender from the properties, and without
 * that the log.
 *
 * <p>Deliberately an auto-configuration of its own with {@code after =
 * MailSenderAutoConfiguration} rather than simply beans in
 * {@code IdentityBeans}: {@code @ConditionalOnBean} decides from what is
 * <em>already</em> registered at the time it is evaluated. Inside a
 * {@code @Configuration} pulled in through {@code @Import}, that check runs
 * before Spring Boot has created the {@code JavaMailSender}, so the condition
 * is always false. The same trap sits between the beans of this very class:
 * the sender must not be <em>conditional</em> on a transport bean, because
 * the transport is registered in the same round. It therefore takes the
 * transport through an {@code ObjectProvider} at creation time and falls back
 * to the log when none is there -- which is also when the fallback's warning
 * is right.
 *
 * <p><strong>Split in two because {@code spring-boot-starter-mail} is optional
 * as of 0.7.0.</strong> Everything that touches a mail library sits in
 * {@link JavaMail} under {@code @ConditionalOnClass}; without the library that
 * class is never loaded. The reference to {@code MailSenderAutoConfiguration}
 * is a string for the same reason ({@code afterName}).
 */
@AutoConfiguration(afterName = "org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration")
public class IdentityMailAutoConfiguration {

    /** The normal case: the application brings the mail library and has set {@code spring.mail.*}. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JavaMailSender.class)
    static class JavaMail {

        @Bean
        @ConditionalOnBean(JavaMailSender.class)
        @ConditionalOnMissingBean(IdentityMailTransport.class)
        IdentityMailTransport javaMailIdentityMailTransport(JavaMailSender mailSender,
                                                            IdentityProperties properties) {
            return JavaMailFactory.transport(mailSender, properties);
        }
    }

    /**
     * The default sender over whichever transport there is: the application's
     * own, the {@code JavaMailSender} one, or -- without any -- the log, so that
     * the application at least starts and stays usable. Not created next to a
     * sender of the application's own.
     */
    @Bean
    @ConditionalOnMissingBean(IdentityMailSender.class)
    IdentityMailSender identityMailSender(ObjectProvider<IdentityMailTransport> transport,
                                          IdentityProperties properties, IdentityMessages messages) {
        return IdentityMailFactory.viaTransport(
                transport.getIfAvailable(IdentityMailFactory::logOnlyTransport), properties, messages);
    }
}
