package de.zettsystems.identity.application;

import de.zettsystems.identity.values.RoleDefinition;

import java.util.Set;

/**
 * Die zwei Rollen, die jede Anwendung braucht: eine für die Systemverwaltung,
 * eine als Voreinstellung für neue Konten. Alles Fachliche kommt aus den
 * {@link RoleCatalog}-Beans der Anwendung.
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
