package de.zettsystems.identity.application;

import de.zettsystems.identity.values.IdentityProperties;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Baut den Versand über {@code JavaMailSender} — die einzige Klasse dieses
 * Bausteins, die eine Mail-Bibliothek in der Signatur trägt.
 *
 * <p>Getrennt von {@link IdentityMailFactory}, weil {@code
 * spring-boot-starter-mail} seit 0.7.0 optional ist: Diese Klasse wird nur
 * geladen, wenn die Auto-Konfiguration sie über {@code @ConditionalOnClass}
 * überhaupt erreicht. Eine Anwendung ohne Mailversand bekommt sie nie zu
 * sehen.
 */
public final class JavaMailFactory {

    private JavaMailFactory() {
        // Fabrikmethode
    }

    public static IdentityMailSender javaMail(JavaMailSender mailSender, IdentityProperties properties,
                                              IdentityMessages messages) {
        return new JavaMailIdentityMailSender(mailSender, properties, messages);
    }
}
