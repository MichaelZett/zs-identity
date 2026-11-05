package de.zettsystems.identity.domain;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /**
     * Loads an account together with its roles. The sign-in path needs the
     * roles immediately; without the entity graph it runs into a
     * LazyInitializationException outside the transaction.
     */
    @EntityGraph(attributePaths = {"roleAssignments", "roleAssignments.role",
            "roleAssignments.role.authorities"})
    Optional<UserAccount> findByEmail(String email);

    /**
     * One account together with its roles in one statement. Wherever an
     * account becomes a DTO, this is the lookup by id: the inherited
     * {@code findById} would fetch the roles one by one afterwards. Cacheable:
     * with Hibernate's query cache on, a repeated lookup needs no statement;
     * without it the hint does nothing.
     */
    @EntityGraph(attributePaths = {"roleAssignments", "roleAssignments.role"})
    @QueryHints(@QueryHint(name = HibernateHints.HINT_CACHEABLE, value = "true"))
    Optional<UserAccount> findWithRolesById(Long id);

    boolean existsByEmail(String email);

    /**
     * The account for counting a failed sign-in, with the row locked until the
     * transaction ends. Guesses arrive in parallel; with the optimistic lock
     * alone the second of two would fail instead of counting.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.email = :email")
    Optional<UserAccount> findForUpdateByEmail(@Param("email") String email);

    /** The account behind a WebAuthn user handle; the sign-in with a passkey ends here. */
    Optional<UserAccount> findByPasskeyUserHandle(String passkeyUserHandle);

    @EntityGraph(attributePaths = {"roleAssignments", "roleAssignments.role"})
    List<UserAccount> findAllByOrderByDisplayNameAsc();

    /** Several accounts including roles in one query, for member lists; cacheable like {@link #findWithRolesById}. */
    @EntityGraph(attributePaths = {"roleAssignments", "roleAssignments.role"})
    @QueryHints(@QueryHint(name = HibernateHints.HINT_CACHEABLE, value = "true"))
    List<UserAccount> findAllWithRolesByIdIn(Collection<Long> ids);
}
