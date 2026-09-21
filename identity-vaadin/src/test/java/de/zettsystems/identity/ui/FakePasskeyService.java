package de.zettsystems.identity.ui;

import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.PasskeyService;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.PasskeyDto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Holds passkeys in memory and records what the view deletes. */
class FakePasskeyService implements PasskeyService {

    final List<PasskeyDto> passkeys = new ArrayList<>();
    final List<Long> deleted = new ArrayList<>();
    Long owner = 7L;

    PasskeyDto add(long id, String label, Instant createdAt, Instant lastUsedAt) {
        PasskeyDto passkey = new PasskeyDto(id, label, createdAt, lastUsedAt);
        passkeys.add(passkey);
        return passkey;
    }

    @Override
    public List<PasskeyDto> findAllOf(Long userId) {
        return userId.equals(owner) ? List.copyOf(passkeys) : List.of();
    }

    @Override
    public long countFor(Long userId) {
        return findAllOf(userId).size();
    }

    @Override
    public Set<Long> accountsWithPasskeys(Collection<Long> userIds) {
        return userIds.stream().filter(id -> countFor(id) > 0).collect(Collectors.toSet());
    }

    @Override
    public void delete(Long userId, Long passkeyId) {
        boolean removed = userId.equals(owner) && passkeys.removeIf(passkey -> passkey.id().equals(passkeyId));
        if (!removed) {
            throw new IdentityException(IdentityMessageKeys.PASSKEY_NOT_FOUND, "no such passkey");
        }
        deleted.add(passkeyId);
    }
}
