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
 * @param mustChangePassword das Konto muss sein Passwort ändern, bevor es die
 *                           Anwendung benutzt (Startpasswort, Rücksetzung
 *                           von Hand)
 */
public record UserAccountDto(Long id,
                             @Nullable String email,
                             AccountName name,
                             boolean enabled,
                             boolean emailVerified,
                             Instant createdAt,
                             Set<String> roleCodes,
                             boolean mustChangePassword) {

    public UserAccountDto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(roleCodes, "roleCodes");
        roleCodes = Set.copyOf(roleCodes);
    }

    /**
     * Die Gestalt vor 0.3.0 — ohne {@code mustChangePassword}. Bleibt, damit
     * Anwendungen, die das Record von Hand bauen (in Tests üblich), nicht
     * brechen.
     */
    public UserAccountDto(Long id, @Nullable String email, AccountName name, boolean enabled,
                          boolean emailVerified, Instant createdAt, Set<String> roleCodes) {
        this(id, email, name, enabled, emailVerified, createdAt, roleCodes, false);
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
        String firstName = name.firstName();
        return firstName != null ? firstName : "";
    }

    /** Nachname; leer, wenn die Anwendung nur Anzeigenamen führt. */
    public String lastName() {
        String lastName = name.lastName();
        return lastName != null ? lastName : "";
    }

    /** Verwaltet = von der Anwendung angelegt, ohne eigene Anmeldedaten. */
    public boolean managed() {
        return email == null;
    }

    public boolean hasRole(String roleCode) {
        return roleCodes.contains(roleCode);
    }
}
