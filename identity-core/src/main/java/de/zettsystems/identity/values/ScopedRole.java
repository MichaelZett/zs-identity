package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;

/**
 * A role assignment as it is visible from the outside: the role code and the
 * scope it applies to.
 *
 * <p>{@link Serializable} like {@link Scope}: assignments travel with the
 * account DTO through views that outlive a single request.
 *
 * @param roleCode role code without the {@code ROLE_} prefix
 * @param scope    the scope; {@code null} means <strong>global</strong>, in
 *                 which case the role applies everywhere
 */
public record ScopedRole(String roleCode, @Nullable Scope scope) implements Serializable {

    public ScopedRole {
        Objects.requireNonNull(roleCode, "roleCode");
    }

    /** A role that applies everywhere. */
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
     * The name Spring Security knows this assignment by: {@code ROLE_ADMIN}
     * when global, {@code ROLE_ADMIN@club:17} when scoped.
     *
     * <p>The scope is appended rather than prepended so that the {@code ROLE_}
     * prefix and the role code stay untouched. Expressions such as
     * {@code hasAuthority("ROLE_ADMIN@club:17")} remain readable, and
     * {@link Scope} forbids exactly the characters that could smuggle
     * something in here.
     */
    public String authorityName() {
        return qualify("ROLE_" + roleCode);
    }

    /** Like {@link #authorityName()}, but for a fine-grained permission. */
    public String qualify(String authority) {
        return scope == null ? authority : authority + Scope.AUTHORITY_SEPARATOR + scope;
    }
}
