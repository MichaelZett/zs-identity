package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * An account was locked for a while after too many wrong passwords in a row
 * (see {@link LoginProtectionSettings}).
 *
 * <p>For applications that want to log it somewhere of their own or tell an
 * administrator. The building block itself writes a WARN line and, unless
 * switched off, sends the account a mail.
 *
 * <p>Deliberately <strong>not</strong> one of the {@link IdentityAccountEvent}
 * shapes next to {@link AccountLocked}. Those are the changes after which
 * whatever an application keeps per account has to go -- the remember-me
 * tokens above all. A temporary lock is none of that: anybody who knows an
 * address can cause one, and if it threw the owner out on every device, a
 * few wrong passwords would be a way to sign someone else out. The owner's
 * running sessions stay; only new password sign-ins are refused until the
 * lock runs out.
 *
 * <p>Published inside the transaction that records the failure, like the
 * other events.
 *
 * @param userId        the account
 * @param email         its sign-in name
 * @param lockedUntil   when the lock runs out by itself
 * @param clientAddress the address the last wrong password came from, as the
 *                      servlet container saw it; {@code null} if the sign-in
 *                      did not come through a web request
 */
public record AccountTemporarilyLocked(Long userId, String email, Instant lockedUntil,
                                       @Nullable String clientAddress) {

    public AccountTemporarilyLocked {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(lockedUntil, "lockedUntil");
    }
}
