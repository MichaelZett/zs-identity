package de.zettsystems.identity.application;

import de.zettsystems.identity.values.IdentityProperties;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Baut die Mail-Implementierungen. Nur da, damit die Auto-Konfiguration nicht
 * auf paket-private Klassen zugreifen muss — {@link JavaMailIdentityMailSender}
 * und {@link LoggingIdentityMailSender} bleiben so im Paket eingeschlossen.
 */
public final class IdentityMailFactory {

    private IdentityMailFactory() {
        // Fabrikmethoden
    }

    public static IdentityMailSender javaMail(JavaMailSender mailSender, IdentityProperties properties,
                                              IdentityMessages messages) {
        return new JavaMailIdentityMailSender(mailSender, properties, messages);
    }

    public static IdentityMailSender logOnly() {
        return new LoggingIdentityMailSender();
    }
}
