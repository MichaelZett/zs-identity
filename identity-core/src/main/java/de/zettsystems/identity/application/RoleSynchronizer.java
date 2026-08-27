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
 * Spiegelt die {@link RoleCatalog}-Beans beim Start in die Datenbank.
 *
 * <p>Idempotent: Neue Rollen werden angelegt, bestehende bekommen Anzeigename
 * und Berechtigungen aus dem Katalog nachgezogen. So wirkt eine Änderung am
 * Katalog ohne eigene Migration.
 *
 * <p>Rollen, die in keinem Katalog mehr vorkommen, bleiben <strong>stehen</strong>.
 * Automatisches Löschen würde bei einem Tippfehler im Katalog schlagartig
 * allen Betroffenen die Rechte entziehen — und wegen des Fremdschlüssels auf
 * {@code auth_user_role} ohnehin scheitern. Verwaiste Rollen räumt eine
 * bewusste Migration ab.
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
     * Vereinigt alle Kataloge. Liefern zwei Beans denselben Code, gewinnt der
     * zuletzt gelesene — das ist der Weg, wie eine Anwendung eine eingebaute
     * Rolle umdefinieren kann.
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
