package de.zettsystems.identity.application;

import de.zettsystems.identity.values.ExternalIdentityDto;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The identities at external providers linked to the accounts, as far as an
 * application sees them (since 1.2.0): a list per account, a marker for
 * member lists, and unlinking.
 *
 * <p>Linking does not go through here: it is a round trip through the
 * provider, which {@code IdentityOAuth2Configurer} wires up and the views of
 * {@code identity-vaadin} start. Available regardless of
 * {@code zs.identity.oauth2.enabled}: with it switched off the lists are
 * simply empty.
 */
public interface ExternalIdentityService {

    /** The identities of an account, oldest first; empty for an unknown account. */
    List<ExternalIdentityDto> findAllOf(Long userId);

    /** Which of these accounts have at least one linked identity, in one query. */
    Set<Long> accountsWithExternalIdentities(Collection<Long> userIds);

    /**
     * Unlinks the account from its identity at this provider. The account
     * id is part of the call on purpose, as with passkeys: nobody unlinks
     * somebody else's account by naming a provider.
     *
     * @throws IdentityException with {@code ACCOUNT_NOT_FOUND} for an unknown
     *                           account, {@code EXTERNAL_IDENTITY_NOT_FOUND}
     *                           if it has no identity there, and
     *                           {@code LAST_SIGN_IN_METHOD} if it would be
     *                           left without any way in -- no password, no
     *                           passkey, no other provider
     */
    void unlink(Long userId, String registrationId);
}
