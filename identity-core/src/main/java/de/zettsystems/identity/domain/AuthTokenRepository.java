package de.zettsystems.identity.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    /**
     * The user is fetched along with it: every redemption path continues with
     * the account right afterwards.
     */
    @EntityGraph(attributePaths = {"user"})
    Optional<AuthToken> findByTokenHashAndType(String tokenHash, AuthTokenType type);

    /**
     * Voids every open token of one type for a user. Called before issuing a
     * new token, so that only the most recently sent link is ever valid.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuthToken t
               set t.usedAt = :now
             where t.user.id = :userId
               and t.type = :type
               and t.usedAt is null
            """)
    int invalidateOpenTokens(@Param("userId") Long userId,
                             @Param("type") AuthTokenType type,
                             @Param("now") Instant now);

    /** Clears out expired or redeemed tokens. */
    @Modifying
    @Query("delete from AuthToken t where t.expiresAt < :cutoff or t.usedAt is not null")
    int deleteObsolete(@Param("cutoff") Instant cutoff);
}
