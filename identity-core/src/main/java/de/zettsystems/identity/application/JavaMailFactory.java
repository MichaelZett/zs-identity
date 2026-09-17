package de.zettsystems.identity.application;

import de.zettsystems.identity.values.IdentityProperties;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Builds delivery through {@code JavaMailSender}: the only class in this
 * building block that carries a mail library in its signature.
 *
 * <p>Separated from {@link IdentityMailFactory} because {@code
 * spring-boot-starter-mail} is optional as of 0.7.0. This class is loaded only
 * when the auto-configuration reaches it through {@code @ConditionalOnClass}
 * at all. An application without mail delivery never sees it.
 */
public final class JavaMailFactory {

    private JavaMailFactory() {
        // Factory method
    }

    public static IdentityMailSender javaMail(JavaMailSender mailSender, IdentityProperties properties,
                                              IdentityMessages messages) {
        return new JavaMailIdentityMailSender(mailSender, properties, messages);
    }
}
