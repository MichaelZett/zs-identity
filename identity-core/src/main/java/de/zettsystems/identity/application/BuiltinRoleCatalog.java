package de.zettsystems.identity.application;

import de.zettsystems.identity.values.RoleDefinition;

import java.util.Set;

/**
 * The two roles every application needs: one for system administration, one as
 * the default for new accounts. Everything domain-specific comes from the
 * application's {@link RoleCatalog} beans.
 */
class BuiltinRoleCatalog implements RoleCatalog {

    static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    static final String USER = "USER";

    @Override
    public Set<RoleDefinition> roles() {
        return Set.of(
                RoleDefinition.of(SYSTEM_ADMIN, "identity.role.systemAdmin"),
                RoleDefinition.of(USER, "identity.role.user"));
    }
}
