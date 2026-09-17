package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.Role;
import de.zettsystems.identity.domain.RoleRepository;
import de.zettsystems.identity.testsupport.AbstractIdentityIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shows that the role catalog of the application really does travel into the
 * database, which is the mechanism that makes this building block reusable.
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

        // A second run with the same catalogs: the synchronizer has to be
        // idempotent, or every restart would multiply the roles.
        new RoleSynchronizer(List.of(new BuiltinRoleCatalog()), roleRepository).run(null);

        assertThat(roleRepository.count()).isEqualTo(before);
    }
}
