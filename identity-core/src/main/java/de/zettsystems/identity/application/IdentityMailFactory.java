package de.zettsystems.identity.application;

/**
 * Baut die Mail-Implementierungen, die ohne Mail-Bibliothek auskommen. Nur da,
 * damit die Auto-Konfiguration nicht auf paket-private Klassen zugreifen muss —
 * {@link LoggingIdentityMailSender} bleibt so im Paket eingeschlossen.
 *
 * <p><strong>Kein Mail-Typ in dieser Klasse.</strong> Seit 0.7.0 ist
 * {@code spring-boot-starter-mail} eine optionale Abhaengigkeit; wer sie nicht
 * mitbringt, bekommt den Rückfall hier. Läge daneben eine Methode mit
 * {@code JavaMailSender} in der Signatur, hinge das Laden dieser Klasse an
 * einer Bibliothek, die es dann gar nicht gibt. Die Mail-Variante steht
 * deshalb in {@link JavaMailFactory}.
 */
public final class IdentityMailFactory {

    private IdentityMailFactory() {
        // Fabrikmethoden
    }

    public static IdentityMailSender logOnly() {
        return new LoggingIdentityMailSender();
    }
}
