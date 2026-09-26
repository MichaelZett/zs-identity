package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * An account was unlinked from its identity at an external provider (since
 * 1.2.0).
 *
 * <p>A way into the account is gone, which counts like a changed password:
 * whatever let a device in without asking -- the remember-me tokens above all
 * -- goes with it ({@code RememberMeTokenCleaner} listens). Not one of the
 * {@link IdentityAccountEvent} shapes, so that exhaustive switches over those
 * keep compiling.
 *
 * @param userId         the account
 * @param email          its sign-in name
 * @param registrationId the client registration of the provider
 */
public record ExternalIdentityUnlinked(Long userId, @Nullable String email, String registrationId) {

    public ExternalIdentityUnlinked {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(registrationId, "registrationId");
    }
}
