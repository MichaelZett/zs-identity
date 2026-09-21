package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

/**
 * The password of an account was set to a new value: changed while signed in,
 * reset through the link in the mail, or set for the first time when an
 * invitation was redeemed.
 *
 * <p>Whoever changes their password after losing a device expects that device
 * to be locked out. Anything that lets someone back in without the password --
 * a remember-me cookie above all -- therefore has to go; see
 * {@code RememberMeTokenCleaner}.
 *
 * @param userId the account
 * @param email  its sign-in name, {@code null} for a managed account (an
 *               administrator can set a password for one, but it still has no
 *               address to sign in with)
 */
public record PasswordChanged(Long userId, @Nullable String email) implements IdentityAccountEvent {
}
