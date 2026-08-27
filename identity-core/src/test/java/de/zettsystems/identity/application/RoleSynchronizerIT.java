package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.Role;
import de.zettsystems.identity.domain.RoleRepository;
import de.zettsystems.identity.testsupport.AbstractIdentityIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Belegt, dass der Rollenkatalog der Anwendung tatsächlich in die Datenbank
 * wandert — das ist der Mechanismus, der diesen Baustein wiederverwendbar macht.
 */
class RoleSynchronizerIT extends AbstractIdentityIntegrationTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void builtinAndApplicationRolesAreBothPresentAfterStartup() {
        assertThat(roleRepository.findAllByOrderByCodeAsc())
                .extracting(Role::getCode)
                .contains("SYSTEM_ADMIN", "USER", "GROUP_ADMIN", "MEMBER");
    }

    @Test
    void authoritiesFromTheCatalogAreStoredWithTheRole() {
        Role member = roleRepository.findByCode("MEMBER").orElseThrow();

        assertThat(member.getAuthorities()).containsExactly("season:read");
        assertThat(member.getDisplayNameKey()).isEqualTo("role.member");
    }

    @Test
    void synchronisingAgainChangesNothing() {
        long before = roleRepository.count();

        // Zweiter Lauf mit denselben Katalogen — der Synchronizer muss idempotent
        // sein, sonst würde jeder Neustart Rollen vervielfachen.
        new RoleSynchronizer(java.util.List.of(new BuiltinRoleCatalog()), roleRepository).run(null);

        assertThat(roleRepository.count()).isEqualTo(before);
    }
}
