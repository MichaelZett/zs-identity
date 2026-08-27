package de.zettsystems.identity.application;

import de.zettsystems.identity.values.UserAccountDto;

/**
 * Versand der Mails dieses Bausteins.
 *
 * <p>Ein Port, keine feste Implementierung: Die Voreinstellung schreibt über
 * {@code JavaMailSender}, eine Anwendung mit eigenem Versandweg (Transaktionsmail-
 * Dienst, Warteschlange, Testdoppel) stellt einfach eine eigene Bean bereit und
 * verdrängt damit die Voreinstellung.
 */
public interface IdentityMailSender {

    /**
     * Bittet um Bestätigung der Adresse.
     *
     * @param confirmationUrl vollständige Adresse inklusive Token
     */
    void sendEmailVerification(UserAccountDto user, String confirmationUrl);

    /**
     * Schickt den Link zum Zurücksetzen des Passworts.
     *
     * @param resetUrl vollständige Adresse inklusive Token
     */
    void sendPasswordReset(UserAccountDto user, String resetUrl);
}
