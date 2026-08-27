package de.zettsystems.identity.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    @EntityGraph(attributePaths = {"authorities"})
    Optional<Role> findByCode(String code);

    @EntityGraph(attributePaths = {"authorities"})
    List<Role> findAllByOrderByCodeAsc();
}
