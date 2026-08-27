package de.zettsystems.identity.domain;

/** Wozu ein {@link AuthToken} berechtigt. */
public enum AuthTokenType {

    /** Bestätigt, dass die angegebene E-Mail-Adresse dem Konto gehört. */
    EMAIL_VERIFICATION,

    /** Erlaubt einmalig das Setzen eines neuen Passworts. */
    PASSWORD_RESET
}
