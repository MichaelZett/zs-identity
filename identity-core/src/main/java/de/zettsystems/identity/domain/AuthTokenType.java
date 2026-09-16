package de.zettsystems.identity.domain;

/** Wozu ein {@link AuthToken} berechtigt. */
public enum AuthTokenType {

    /** Bestätigt, dass die angegebene E-Mail-Adresse dem Konto gehört. */
    EMAIL_VERIFICATION,

    /** Erlaubt einmalig das Setzen eines neuen Passworts. */
    PASSWORD_RESET,

    /**
     * Lädt eine Person ein, ein Konto zu beanspruchen: Adresse bestätigen und
     * erstes Passwort setzen. Gilt länger als die übrigen Token
     * ({@code zs.identity.invitation-validity}) — eine Einladung liest niemand
     * so schnell wie eine selbst angeforderte Mail.
     */
    INVITATION
}
