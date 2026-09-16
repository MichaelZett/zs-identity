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

    /**
     * Lädt zu einem Konto ein: Adresse bestätigen und erstes Passwort setzen.
     *
     * <p>Bewusst eine {@code default}-Methode, die scheitert, statt einer
     * abstrakten: Ein eigener Sender einer Anwendung (Transaktionsmail-Dienst,
     * Testdoppel) soll durch diese Ergänzung nicht die Übersetzung verlieren.
     * Still nichts zu tun wäre schlimmer als der Fehler — der Eingeladene
     * bekäme nie einen Link, und niemand merkte es.
     *
     * @param invitationUrl vollständige Adresse inklusive Token
     * @throws UnsupportedOperationException solange ein eigener Sender sie nicht überschreibt
     */
    default void sendInvitation(UserAccountDto user, String invitationUrl) {
        throw new UnsupportedOperationException(
                getClass().getName() + " does not implement sendInvitation(..) — "
                        + "implement it to use InvitationService");
    }
}
