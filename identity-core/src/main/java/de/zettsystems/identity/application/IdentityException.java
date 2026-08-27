package de.zettsystems.identity.application;

import java.io.Serial;

/**
 * Fachlicher Fehler des Identity-Bausteins — etwas, das der Oberfläche als
 * verständliche Meldung angezeigt werden darf (Adresse schon vergeben, Token
 * abgelaufen, Passwort zu kurz).
 *
 * <p>Trägt einen {@code messageKey}, damit die Anwendung die Meldung in ihrer
 * eigenen Sprache ausgeben kann; {@code getMessage()} liefert einen englischen
 * Klartext fürs Log.
 */
public class IdentityException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String messageKey;

    public IdentityException(String messageKey, String message) {
        super(message);
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
