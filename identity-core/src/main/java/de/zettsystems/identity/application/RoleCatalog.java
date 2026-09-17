package de.zettsystems.identity.application;

import de.zettsystems.identity.values.RoleDefinition;

import java.util.Set;

/**
 * Supplies the roles an application needs.
 *
 * <p>This is the reason this building block is reusable: only the application
 * knows which roles exist. It provides a bean, and at startup the building
 * block mirrors the catalog into the database idempotently. A hard-wired enum
 * inside the building block would already be wrong in the second application.
 *
 * <p>The building block brings its own base roles through
 * {@code BuiltinRoleCatalog}; the catalogs of all beans are merged. An example
 * from an application:
 *
 * <pre>
 * &#64;Component
 * class HallenplanungRoleCatalog implements RoleCatalog {
 *     &#64;Override
 *     public Set&lt;RoleDefinition&gt; roles() {
 *         return Set.of(RoleDefinition.of("GROUP_ADMIN", "role.groupAdmin"),
 *                       RoleDefinition.of("MEMBER", "role.member"));
 *     }
 * }
 * </pre>
 *
 * <p>Roles that no catalog lists any more stay in the database. Deleting them
 * automatically would, on a single typo in a catalog, strip the rights from
 * everyone holding them at once.
 */
@FunctionalInterface
public interface RoleCatalog {

    Set<RoleDefinition> roles();
}
