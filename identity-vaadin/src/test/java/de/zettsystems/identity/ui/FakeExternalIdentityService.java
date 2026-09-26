package de.zettsystems.identity.ui;

import de.zettsystems.identity.application.ExternalIdentityService;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.values.ExternalIdentityDto;
import de.zettsystems.identity.values.IdentityMessageKeys;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Holds linked identities in memory and records what the view unlinks; see {@link FakePasskeyService}. */
class FakeExternalIdentityService implements ExternalIdentityService {

    final List<ExternalIdentityDto> identities = new ArrayList<>();
    final List<String> unlinked = new ArrayList<>();
    Long owner = 7L;
    /** When set, the next {@link #unlink} fails with it. */
    IdentityException failure;

    @Override
    public List<ExternalIdentityDto> findAllOf(Long userId) {
        return userId.equals(owner) ? List.copyOf(identities) : List.of();
    }

    @Override
    public Set<Long> accountsWithExternalIdentities(Collection<Long> userIds) {
        return userIds.stream().filter(id -> !findAllOf(id).isEmpty()).collect(Collectors.toSet());
    }

    @Override
    public void unlink(Long userId, String registrationId) {
        if (failure != null) {
            IdentityException thrown = failure;
            failure = null;
            throw thrown;
        }
        boolean removed = userId.equals(owner)
                && identities.removeIf(identity -> identity.registrationId().equals(registrationId));
        if (!removed) {
            throw new IdentityException(IdentityMessageKeys.EXTERNAL_IDENTITY_NOT_FOUND, "no such identity");
        }
        unlinked.add(registrationId);
    }
}
