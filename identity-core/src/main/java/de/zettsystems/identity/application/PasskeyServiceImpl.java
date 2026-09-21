package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.Passkey;
import de.zettsystems.identity.domain.PasskeyRepository;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.PasskeyDto;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

class PasskeyServiceImpl implements PasskeyService {

    private final PasskeyRepository passkeys;

    PasskeyServiceImpl(PasskeyRepository passkeys) {
        this.passkeys = passkeys;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PasskeyDto> findAllOf(Long userId) {
        return passkeys.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(PasskeyServiceImpl::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countFor(Long userId) {
        return passkeys.countByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> accountsWithPasskeys(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(passkeys.userIdsWithPasskeys(userIds));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long passkeyId) {
        Passkey passkey = passkeys.findByIdAndUserId(passkeyId, userId)
                .orElseThrow(() -> new IdentityException(IdentityMessageKeys.PASSKEY_NOT_FOUND,
                        "Account %d has no passkey with id %d".formatted(userId, passkeyId)));
        passkeys.delete(passkey);
    }

    static PasskeyDto toDto(Passkey passkey) {
        return new PasskeyDto(Objects.requireNonNull(passkey.getId(), "passkey.id"), passkey.getLabel(),
                passkey.getCreatedAt(), passkey.getLastUsedAt());
    }
}
