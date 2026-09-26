package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * An account was linked to an identity at an external provider (since
 * 1.2.0): on the first sign-in through it, when an invitation was redeemed
 * through it, or from within the account.
 *
 * <p>For applications that want to log it or tell the person; from now on
 * the provider is a way into the account. Deliberately not one of the
 * {@link IdentityAccountEvent} shapes: nothing an application keeps per
 * account has to go because a way in was added, and exhaustive switches over
 * those keep compiling.
 *
 * @param userId         the account
 * @param email          its sign-in name
 * @param registrationId the client registration of the provider
 */
public record ExternalIdentityLinked(Long userId, @Nullable String email, String registrationId) {

    public ExternalIdentityLinked {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(registrationId, "registrationId");
    }
}
