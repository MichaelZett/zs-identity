package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Eine Rollenzuweisung, wie sie nach außen sichtbar ist: Rollencode und der
 * Bereich, in dem er gilt.
 *
 * <p>{@link Serializable} wie {@link Scope} — Zuweisungen wandern mit dem
 * Konto-Dto durch Ansichten, die eine Sitzung überdauern.
 *
 * @param roleCode Rollencode ohne {@code ROLE_}-Präfix
 * @param scope    Geltungsbereich; {@code null} heißt <strong>global</strong>,
 *                 die Rolle gilt dann überall
 */
public record ScopedRole(String roleCode, @Nullable Scope scope) implements Serializable {

    public ScopedRole {
        Objects.requireNonNull(roleCode, "roleCode");
    }

    /** Eine Rolle, die überall gilt. */
    public static ScopedRole global(String roleCode) {
        return new ScopedRole(roleCode, null);
    }

    public static ScopedRole of(String roleCode, Scope scope) {
        return new ScopedRole(roleCode, Objects.requireNonNull(scope, "scope"));
    }

    public boolean isGlobal() {
        return scope == null;
    }

    /**
     * Der Name, unter dem Spring Security diese Zuweisung kennt:
     * {@code ROLE_ADMIN} global, {@code ROLE_ADMIN@club:17} mit Bereich.
     *
     * <p>Der Bereich hängt hinten dran, weil {@code ROLE_}-Präfix und
     * Rollencode dadurch unverändert bleiben — Ausdrücke wie
     * {@code hasAuthority("ROLE_ADMIN@club:17")} lesen sich noch, und
     * {@link Scope} verbietet die Zeichen, mit denen sich hier etwas
     * einschmuggeln ließe.
     */
    public String authorityName() {
        return qualify("ROLE_" + roleCode);
    }

    /** Wie {@link #authorityName()}, aber für eine feingranulare Berechtigung. */
    public String qualify(String authority) {
        return scope == null ? authority : authority + Scope.AUTHORITY_SEPARATOR + scope;
    }
}
