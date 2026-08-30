package de.zettsystems.identity.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /**
     * Lädt ein Konto samt Rollen. Der Anmeldepfad braucht die Rollen sofort;
     * ohne den EntityGraph läuft er außerhalb der Transaktion in eine
     * LazyInitializationException.
     */
    @EntityGraph(attributePaths = {"roles", "roles.authorities"})
    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = {"roles"})
    List<UserAccount> findAllByOrderByDisplayNameAsc();

    /** Mehrere Konten samt Rollen in einer Abfrage — für Mitgliederlisten. */
    @EntityGraph(attributePaths = {"roles"})
    List<UserAccount> findAllWithRolesByIdIn(Collection<Long> ids);
}
