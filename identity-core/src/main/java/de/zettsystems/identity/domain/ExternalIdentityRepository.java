package de.zettsystems.identity.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, Long> {

    /**
     * The sign-in path: the provider names the identity, the account comes
     * along with its roles, which the principal needs right away.
     */
    @EntityGraph(attributePaths = {"user", "user.roleAssignments", "user.roleAssignments.role",
            "user.roleAssignments.role.authorities"})
    Optional<ExternalIdentity> findByRegistrationIdAndSubject(String registrationId, String subject);

    /** The identities of an account, oldest first, as a list shows them. */
    List<ExternalIdentity> findAllByUserIdOrderByCreatedAtAsc(Long userId);

    /** Which of these accounts have at least one linked identity, in one query for member lists. */
    @Query("select distinct e.user.id from ExternalIdentity e where e.user.id in :userIds")
    Set<Long> userIdsWithExternalIdentities(@Param("userIds") Collection<Long> userIds);
}
