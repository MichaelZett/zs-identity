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
     * Der Benutzer wird mitgeladen: Jeder Einlösepfad arbeitet direkt danach
     * mit dem Konto weiter.
     */
    @EntityGraph(attributePaths = {"user"})
    Optional<AuthToken> findByTokenHashAndType(String tokenHash, AuthTokenType type);

    /**
     * Entwertet alle offenen Token eines Typs für einen Benutzer. Wird vor dem
     * Ausstellen eines neuen Tokens aufgerufen, damit immer nur der zuletzt
     * verschickte Link gilt.
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

    /** Räumt abgelaufene oder eingelöste Token ab. */
    @Modifying
    @Query("delete from AuthToken t where t.expiresAt < :cutoff or t.usedAt is not null")
    int deleteObsolete(@Param("cutoff") Instant cutoff);
}
