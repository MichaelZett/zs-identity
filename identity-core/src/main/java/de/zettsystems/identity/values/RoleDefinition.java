package de.zettsystems.identity.values;

import java.util.Objects;
import java.util.Set;

/**
 * Beschreibt eine Rolle, die die einbindende Anwendung braucht.
 *
 * @param code           technischer Schlüssel, landet als {@code ROLE_<code>} in
 *                       den Spring-Security-Authorities. Konvention: GROSS_MIT_UNTERSTRICH.
 * @param displayNameKey i18n-Schlüssel für die Anzeige. Der Baustein löst ihn
 *                       nicht selbst auf — welche Sprachdateien es gibt, weiß
 *                       nur die Anwendung.
 * @param authorities    feingranulare Berechtigungen, die an der Rolle hängen.
 *                       Landen unverändert als Authorities neben der Rolle.
 */
public record RoleDefinition(String code, String displayNameKey, Set<String> authorities) {

    public RoleDefinition {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        Objects.requireNonNull(authorities, "authorities");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        authorities = Set.copyOf(authorities);
    }

    /** Rolle ohne eigene Berechtigungen — der häufige Fall. */
    public static RoleDefinition of(String code, String displayNameKey) {
        return new RoleDefinition(code, displayNameKey, Set.of());
    }

    /** Wie der Code in den Spring-Security-Authorities auftaucht. */
    public String authorityName() {
        return "ROLE_" + code;
    }
}
