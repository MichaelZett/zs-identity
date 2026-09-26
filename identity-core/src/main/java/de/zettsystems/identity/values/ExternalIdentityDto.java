package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * An identity at an external provider linked to an account, as far as an
 * application needs to know it (since 1.2.0): enough for a list, and the
 * registration id to unlink by.
 *
 * @param registrationId the client registration the identity belongs to
 *                       ({@code google}, {@code github})
 * @param email          the address the provider reported at the last
 *                       sign-in; {@code null} if it reported none
 * @param createdAt      when the identity was linked
 * @param lastUsedAt     when it was last used to sign in; {@code null} until
 *                       then
 */
public record ExternalIdentityDto(String registrationId, @Nullable String email, Instant createdAt,
                                  @Nullable Instant lastUsedAt) {

    public ExternalIdentityDto {
        Objects.requireNonNull(registrationId, "registrationId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
