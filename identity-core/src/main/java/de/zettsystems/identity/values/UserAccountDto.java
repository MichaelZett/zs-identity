package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Sicht auf ein Benutzerkonto für alles außerhalb dieses Bausteins.
 *
 * <p>Die JPA-Entity verlässt das Modul bewusst nicht: Anwendungen sollen über
 * die {@code id} verknüpfen, nicht über eine Objektreferenz. Das hält den
 * Baustein austauschbar und vermeidet abgelöste Entities in fremden
 * Transaktionen.
 *
 * @param email {@code null} bei verwalteten Konten — Personen, die eine
 *              Anwendung ohne Selbstregistrierung führt ({@link #managed()})
 * @param name  Anzeigename, bei Klarnamen-Anwendungen auch Vor- und Nachname
 * @param roleCodes Rollencodes ohne {@code ROLE_}-Präfix
 */
public record UserAccountDto(Long id,
                             @Nullable String email,
                             AccountName name,
                             boolean enabled,
                             boolean emailVerified,
                             Instant createdAt,
                             Set<String> roleCodes) {

    public UserAccountDto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(roleCodes, "roleCodes");
        roleCodes = Set.copyOf(roleCodes);
    }

    /** Der öffentlich sichtbare Name — in beiden Namensgestalten gesetzt. */
    public String displayName() {
        return name.displayName();
    }

    /**
     * Vorname; leer, wenn die Anwendung nur Anzeigenamen führt. Nicht
     * {@code null}, damit Klarnamen-Anwendungen ohne Fallunterscheidung
     * arbeiten können.
     */
    public String firstName() {
        return Objects.requireNonNullElse(name.firstName(), "");
    }

    /** Nachname; leer, wenn die Anwendung nur Anzeigenamen führt. */
    public String lastName() {
        return Objects.requireNonNullElse(name.lastName(), "");
    }

    /** Verwaltet = von der Anwendung angelegt, ohne eigene Anmeldedaten. */
    public boolean managed() {
        return email == null;
    }

    public boolean hasRole(String roleCode) {
        return roleCodes.contains(roleCode);
    }
}
