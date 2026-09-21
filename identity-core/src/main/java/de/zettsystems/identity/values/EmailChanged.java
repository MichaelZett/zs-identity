package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

/**
 * The email address of an account changed -- and with it the name it signs in
 * under.
 *
 * <p>Today the building block only ever <em>assigns</em> an address, to a
 * managed account that is being invited, so {@link #previousEmail()} is
 * {@code null}. The component exists all the same: everything that keys off
 * the sign-in name (remember-me tokens above all) would otherwise be left
 * pointing at a name nobody signs in under any more, and the day the building
 * block gains a real change of address, the listeners are already in place.
 *
 * @param userId        the account
 * @param previousEmail the name it signed in under so far, {@code null} if it
 *                      had none
 * @param email         the address from now on
 */
public record EmailChanged(Long userId, @Nullable String previousEmail, String email)
        implements IdentityAccountEvent {
}
