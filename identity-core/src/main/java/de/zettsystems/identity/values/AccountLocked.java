package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

/**
 * An account was locked ({@code setEnabled(false)}).
 *
 * <p>Locking that leaves a remember-me cookie working locks nobody out, so the
 * tokens go with it. Unlocking publishes nothing: there is nothing to clean up
 * then, and an application that wants to know watches its own administration
 * screen.
 *
 * @param userId the account
 * @param email  its sign-in name, {@code null} for a managed account
 */
public record AccountLocked(Long userId, @Nullable String email) implements IdentityAccountEvent {
}
