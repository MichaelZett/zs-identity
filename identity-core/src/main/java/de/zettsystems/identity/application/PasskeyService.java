package de.zettsystems.identity.application;

import de.zettsystems.identity.values.PasskeyDto;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The passkeys of the accounts, as far as an application sees them: a list
 * per account, a count for settings and member lists, and deletion.
 *
 * <p>Registering and using a passkey does not go through here. Both are a
 * ceremony between the browser and Spring Security's WebAuthn filters, which
 * the building block wires up in {@code IdentityPasskeyConfigurer}; the views
 * of {@code identity-vaadin} drive them from the browser. This service only
 * answers what an application wants to show.
 *
 * <p>Available regardless of {@code zs.identity.passkeys.enabled}: with
 * passkeys switched off the counts are simply zero.
 */
public interface PasskeyService {

    /** The passkeys of an account, oldest first; empty for an unknown account. */
    List<PasskeyDto> findAllOf(Long userId);

    /** How many passkeys an account has; {@code 0} for an unknown account. */
    long countFor(Long userId);

    /** Whether the account can sign in with a passkey at all. */
    default boolean hasPasskey(Long userId) {
        return countFor(userId) > 0;
    }

    /**
     * Which of these accounts have at least one passkey, in one query --
     * for a member list that marks who has set one up, instead of one
     * {@link #countFor} per row.
     */
    Set<Long> accountsWithPasskeys(Collection<Long> userIds);

    /**
     * Removes a passkey of <em>this</em> account. The account id is part of
     * the call on purpose: a passkey id from the list of another account
     * must not be enough to delete it.
     *
     * @throws IdentityException with {@code PASSKEY_NOT_FOUND} if the account
     *                           has no passkey with this id
     */
    void delete(Long userId, Long passkeyId);
}
