package de.zettsystems.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PasskeyRepository extends JpaRepository<Passkey, Long> {

    /** The sign-in path: the authenticator names its credential, nothing else. */
    Optional<Passkey> findByCredentialId(String credentialId);

    /** The passkeys of an account, oldest first, as a list shows them. */
    List<Passkey> findAllByUserIdOrderByCreatedAtAsc(Long userId);

    /**
     * The passkeys of the account behind a WebAuthn user handle, for the
     * {@code excludeCredentials} list when another one is registered.
     */
    List<Passkey> findAllByUserPasskeyUserHandleOrderByCreatedAtAsc(String passkeyUserHandle);

    /** One passkey of one account; empty when it belongs to somebody else. */
    Optional<Passkey> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /** Which of these accounts have at least one passkey, in one query for member lists. */
    @Query("select distinct p.user.id from Passkey p where p.user.id in :userIds")
    Set<Long> userIdsWithPasskeys(@Param("userIds") Collection<Long> userIds);

    long deleteByCredentialId(String credentialId);
}
