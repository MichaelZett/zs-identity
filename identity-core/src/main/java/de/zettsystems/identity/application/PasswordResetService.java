package de.zettsystems.identity.application;

/** Passwort vergessen und neu setzen. */
public interface PasswordResetService {

    /**
     * Verschickt einen Link zum Zurücksetzen.
     *
     * <p>Meldet bewusst keinen Fehler, wenn es die Adresse nicht gibt: Sonst
     * ließe sich über dieses Formular herausfinden, wer bei uns ein Konto hat.
     * Die Oberfläche zeigt in beiden Fällen dieselbe Bestätigung.
     */
    void requestReset(String email);

    /**
     * Setzt das Passwort mit dem Token aus der Mail neu.
     *
     * @throws IdentityException wenn das Token unbekannt, benutzt oder abgelaufen
     *                           ist oder das Passwort zu kurz
     */
    void resetPassword(String token, String newRawPassword);
}
