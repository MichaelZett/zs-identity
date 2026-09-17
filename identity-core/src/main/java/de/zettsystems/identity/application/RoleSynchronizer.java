package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.Role;
import de.zettsystems.identity.domain.RoleRepository;
import de.zettsystems.identity.values.RoleDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors the {@link RoleCatalog} beans into the database at startup.
 *
 * <p>Idempotent: new roles are created, existing ones have their display name
 * and permissions brought in line with the catalog. A change to the catalog
 * therefore takes effect without a migration of its own.
 *
 * <p>Roles that appear in no catalog any more <strong>stay</strong>. Deleting
 * them automatically would, on a single typo in a catalog, strip the rights
 * from everyone holding them at once -- and would fail anyway because of the
 * foreign key on {@code auth_user_role}. Orphaned roles are cleared out by a
 * deliberate migration.
 */
class RoleSynchronizer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(RoleSynchronizer.class);

    private final List<RoleCatalog> catalogs;
    private final RoleRepository roleRepository;

    RoleSynchronizer(List<RoleCatalog> catalogs, RoleRepository roleRepository) {
        this.catalogs = catalogs;
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, RoleDefinition> wanted = collectDefinitions();

        int created = 0;
        int updated = 0;
        for (RoleDefinition definition : wanted.values()) {
            Role role = roleRepository.findByCode(definition.code()).orElse(null);
            if (role == null) {
                role = new Role(definition.code(), definition.displayNameKey());
                role.syncFromCatalog(definition.displayNameKey(), definition.authorities());
                roleRepository.save(role);
                created++;
            } else {
                role.syncFromCatalog(definition.displayNameKey(), definition.authorities());
                updated++;
            }
        }
        LOG.info("Role catalog synchronised: {} created, {} updated, {} definitions total",
                created, updated, wanted.size());
    }

    /**
     * Merges all catalogs. If two beans supply the same code, the one read last
     * wins; that is how an application can redefine a built-in role.
     */
    private Map<String, RoleDefinition> collectDefinitions() {
        Map<String, RoleDefinition> byCode = new HashMap<>();
        for (RoleCatalog catalog : catalogs) {
            for (RoleDefinition definition : catalog.roles()) {
                RoleDefinition previous = byCode.put(definition.code(), definition);
                if (previous != null && !previous.equals(definition)) {
                    LOG.warn("Role code {} is declared more than once; using the definition from {}",
                            definition.code(), catalog.getClass().getName());
                }
            }
        }
        return byCode;
    }
}
