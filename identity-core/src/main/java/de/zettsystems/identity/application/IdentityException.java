package de.zettsystems.identity.application;

import java.io.Serial;

/**
 * A domain error of the identity building block: something that may be shown
 * to the user as an understandable message (address already taken, token
 * expired, password too short).
 *
 * <p>It carries a {@code messageKey} so that the application can render the
 * message in its own language; {@code getMessage()} returns English plain text
 * for the log.
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
