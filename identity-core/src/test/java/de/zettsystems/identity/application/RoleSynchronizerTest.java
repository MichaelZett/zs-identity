package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.Role;
import de.zettsystems.identity.domain.RoleRepository;
import de.zettsystems.identity.values.RoleDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Two catalogs declaring the same code; {@code RoleSynchronizerIT} covers the way into the database. */
class RoleSynchronizerTest {

    private final RoleRepository roles = mock(RoleRepository.class);

    private static RoleCatalog catalog(String displayNameKey) {
        return () -> Set.of(new RoleDefinition("TWICE", displayNameKey, Set.of()));
    }

    private Role synchronisedFrom(RoleCatalog... catalogs) {
        when(roles.findByCode("TWICE")).thenReturn(Optional.empty());

        new RoleSynchronizer(List.of(catalogs), roles).run(null);

        ArgumentCaptor<Role> saved = ArgumentCaptor.forClass(Role.class);
        verify(roles).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void theCatalogReadLastRedefinesTheRole() {
        assertThat(synchronisedFrom(catalog("role.builtin"), catalog("role.application")).getDisplayNameKey())
                .isEqualTo("role.application");
    }

    @Test
    void anIdenticalDeclarationStaysOneRole() {
        assertThat(synchronisedFrom(catalog("role.same"), catalog("role.same")).getDisplayNameKey())
                .isEqualTo("role.same");
    }
}
