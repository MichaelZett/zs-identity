package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.ExternalIdentity;
import de.zettsystems.identity.domain.ExternalIdentityRepository;
import de.zettsystems.identity.domain.PasskeyRepository;
import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.values.ExternalIdentityDto;
import de.zettsystems.identity.values.ExternalIdentityUnlinked;
import de.zettsystems.identity.values.IdentityMessageKeys;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

class ExternalIdentityServiceImpl implements ExternalIdentityService {

    private final UserAccountRepository userRepository;
    private final ExternalIdentityRepository identityRepository;
    private final PasskeyRepository passkeyRepository;
    private final ApplicationEventPublisher events;

    ExternalIdentityServiceImpl(UserAccountRepository userRepository, ExternalIdentityRepository identityRepository,
                                PasskeyRepository passkeyRepository, ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.passkeyRepository = passkeyRepository;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExternalIdentityDto> findAllOf(Long userId) {
        return identityRepository.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(ExternalIdentityServiceImpl::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> accountsWithExternalIdentities(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(identityRepository.userIdsWithExternalIdentities(userIds));
    }

    /**
     * Counts the ways in before letting one go: the password, every passkey,
     * every provider. With one left, unlinking would lock the owner out for
     * good -- there would be nothing left to sign in with, and "forgot
     * password" is no help to an account that never had one it remembers.
     */
    @Override
    @Transactional
    public void unlink(Long userId, String registrationId) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new IdentityException(IdentityMessageKeys.ACCOUNT_NOT_FOUND,
                        "No account with id %d".formatted(userId)));
        if (user.externalIdentity(registrationId).isEmpty()) {
            throw new IdentityException(IdentityMessageKeys.EXTERNAL_IDENTITY_NOT_FOUND,
                    "Account %d has no identity at %s".formatted(userId, registrationId));
        }
        long waysIn = (user.hasPassword() ? 1 : 0) + passkeyRepository.countByUserId(userId)
                + user.getExternalIdentities().size();
        if (waysIn <= 1) {
            throw new IdentityException(IdentityMessageKeys.LAST_SIGN_IN_METHOD,
                    "Account %d would be left without a way in".formatted(userId));
        }
        user.unlinkExternalIdentity(registrationId);
        events.publishEvent(new ExternalIdentityUnlinked(userId, user.getEmail(), registrationId));
    }

    static ExternalIdentityDto toDto(ExternalIdentity identity) {
        return new ExternalIdentityDto(identity.getRegistrationId(), identity.getEmail(), identity.getCreatedAt(),
                identity.getLastUsedAt());
    }
}
