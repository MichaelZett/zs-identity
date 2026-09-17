package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
 * @param roleAssignments alle Rollen des Kontos, je mit dem Bereich, in dem
 *                        sie gelten ({@code null} = global). Für Anwendungen
 *                        ohne Geltungsbereiche ist {@link #roleCodes()} die
 *                        einfachere Sicht darauf.
 * @param mustChangePassword das Konto muss sein Passwort ändern, bevor es die
 *                           Anwendung benutzt (Startpasswort, Rücksetzung
 *                           von Hand)
 * @param locale Sprache, in der das Konto angesprochen werden möchte;
 *               {@code null} heißt „keine eigene Wahl" — dann gilt
 *               {@code zs.identity.locale}. Für den häufigen Fall „irgendeine
 *               Sprache, egal welche" gibt es {@link #localeOr(Locale)}.
 */
public record UserAccountDto(Long id,
                             @Nullable String email,
                             AccountName name,
                             boolean enabled,
                             boolean emailVerified,
                             Instant createdAt,
                             Set<ScopedRole> roleAssignments,
                             boolean mustChangePassword,
                             @Nullable Locale locale) {

    public UserAccountDto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(roleAssignments, "roleAssignments");
        roleAssignments = Set.copyOf(roleAssignments);
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

    /**
     * Die Gestalt mit einfachen Rollencodes — alle Rollen gelten dann global.
     * Bleibt aus demselben Grund; Anwendungen ohne Geltungsbereiche bauen das
     * Record weiterhin so.
     */
    public UserAccountDto(Long id, @Nullable String email, AccountName name, boolean enabled,
                          boolean emailVerified, Instant createdAt, Set<String> roleCodes,
                          boolean mustChangePassword) {
        this(id, email, name, enabled, emailVerified, createdAt,
                roleCodes.stream().map(ScopedRole::global).collect(Collectors.toUnmodifiableSet()),
                mustChangePassword, null);
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

    /**
     * Die Sprache des Kontos, und wenn es keine gewählt hat, die übergebene.
     * Spart die Fallunterscheidung überall dort, wo ohnehin eine Sprache
     * gebraucht wird — etwa beim Mailversand.
     */
    public Locale localeOr(Locale fallback) {
        return locale != null ? locale : Objects.requireNonNull(fallback, "fallback");
    }

    /**
     * Die Codes der <strong>globalen</strong> Rollen — derer, die überall
     * gelten.
     *
     * <p>Vor 0.7.0 gab es nur solche; für Anwendungen ohne Geltungsbereiche
     * ist das also unverändert die ganze Antwort. Wer mandantenfähig ist,
     * fragt mit {@link #rolesIn(Scope)} nach.
     */
    public Set<String> roleCodes() {
        return codesIn(null);
    }

    /** Ob das Konto diese Rolle <strong>global</strong> hat. */
    public boolean hasRole(String roleCode) {
        return roleCodes().contains(roleCode);
    }

    /**
     * Ob das Konto diese Rolle in diesem Bereich hat. Eine globale Rolle zählt
     * mit: Sie gilt überall, also auch hier.
     */
    public boolean hasRole(String roleCode, Scope scope) {
        Objects.requireNonNull(scope, "scope");
        return roleAssignments.stream()
                .anyMatch(assignment -> assignment.roleCode().equals(roleCode)
                        && (assignment.isGlobal() || scope.equals(assignment.scope())));
    }

    /** Die Rollencodes, die in diesem Bereich gelten — die globalen eingeschlossen. */
    public Set<String> rolesIn(Scope scope) {
        Objects.requireNonNull(scope, "scope");
        return roleAssignments.stream()
                .filter(assignment -> assignment.isGlobal() || scope.equals(assignment.scope()))
                .map(ScopedRole::roleCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Die Bereiche dieser Art, in denen das Konto eine Rolle hat — „alle
     * Vereine dieser Person". Globale Rollen tauchen hier nicht auf: Sie
     * gehören zu keinem Bereich.
     */
    public Set<Scope> scopesOf(String type) {
        Objects.requireNonNull(type, "type");
        return roleAssignments.stream()
                .map(ScopedRole::scope)
                .filter(scope -> scope != null && scope.type().equals(type))
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> codesIn(@Nullable Scope scope) {
        return roleAssignments.stream()
                .filter(assignment -> Objects.equals(assignment.scope(), scope))
                .map(ScopedRole::roleCode)
                .collect(Collectors.toUnmodifiableSet());
    }
}
