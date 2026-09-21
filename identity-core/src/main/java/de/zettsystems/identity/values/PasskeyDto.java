package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * A passkey of an account, as far as an application needs to know it: enough
 * for a list with a label and dates, and an id to delete by.
 *
 * <p>The key material stays inside the building block. Nothing here allows
 * signing in, and nothing here changes when the credential is used except
 * {@code lastUsedAt}.
 *
 * @param id         the id of the passkey inside the building block, for
 *                   {@code PasskeyService#delete}
 * @param label      the name the person gave the passkey when creating it
 *                   ("iPhone", "Laptop")
 * @param createdAt  when the passkey was registered
 * @param lastUsedAt when it was last used to sign in; {@code null} until the
 *                   first sign-in
 */
public record PasskeyDto(Long id, String label, Instant createdAt, @Nullable Instant lastUsedAt) {

    /** Longer labels are cut when a passkey is stored; nobody reads a longer one in a list. */
    public static final int LABEL_MAX_LENGTH = 128;

    public PasskeyDto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
