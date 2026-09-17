package de.zettsystems.identity.values;

import java.util.Objects;
import java.util.Set;

/**
 * Describes a role that the embedding application needs.
 *
 * @param code           technical key; ends up as {@code ROLE_<code>} in the
 *                       Spring Security authorities. Convention:
 *                       UPPER_CASE_WITH_UNDERSCORES.
 * @param displayNameKey i18n key for display. The building block does not
 *                       resolve it: only the application knows which message
 *                       bundles exist.
 * @param authorities    fine-grained permissions attached to the role. They
 *                       become authorities next to the role, unchanged.
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

    /** A role without permissions of its own, which is the common case. */
    public static RoleDefinition of(String code, String displayNameKey) {
        return new RoleDefinition(code, displayNameKey, Set.of());
    }

    /** How the code appears among the Spring Security authorities. */
    public String authorityName() {
        return "ROLE_" + code;
    }
}
