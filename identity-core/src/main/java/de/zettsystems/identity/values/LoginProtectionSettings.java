package de.zettsystems.identity.values;

import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Protection against password guessing, prefix
 * {@code zs.identity.login-protection}.
 *
 * <p>Two brakes that work together. A <strong>temporary lock</strong> on the
 * account after {@code maxAttempts} wrong passwords in a row, and a
 * <strong>delay</strong> that grows from the first failure, both per account
 * and per client address -- the lock stops guessing at one account, the delay
 * also slows down someone who tries one password on many addresses.
 *
 * <p>The lock is never permanent: a permanent one would let anyone disable
 * any account on purpose, knowing only its address. "Forgot password" lifts
 * it, because the link in the mail proves ownership; so does a successful
 * sign-in with a passkey, which nobody can guess. It is not the same as
 * {@code UserAccountService#setEnabled(userId, false)} and stays separate
 * from it.
 *
 * @param enabled            whether the protection is on. Off means the plain
 *                           sign-in of Spring Security, with no counting and
 *                           no delay.
 * @param maxAttempts        wrong passwords in a row after which the account
 *                           is locked
 * @param lockDuration       how long the first lock lasts. Every further lock
 *                           without a successful sign-in in between lasts
 *                           twice as long as the one before.
 * @param maxLockDuration    the longest a lock can last. Failures older than
 *                           this are forgotten, so the doubling starts over.
 * @param delay              the delay after the first failure. It doubles with
 *                           every further one.
 * @param maxDelay           the longest delay
 * @param maxDelayedRequests how many sign-in requests may wait out a delay at
 *                           the same time. A request beyond that is turned
 *                           down straight away rather than parked, so that
 *                           guessing cannot tie up every thread of the server.
 * @param notifyByMail       whether the account gets a mail when it is locked,
 *                           so that its owner learns why the sign-in fails and
 *                           how to get in (the message on the sign-in page is
 *                           the same for every failure, on purpose)
 */
public record LoginProtectionSettings(@DefaultValue("true") boolean enabled,
                                      @DefaultValue("3") int maxAttempts,
                                      @DefaultValue("15m") Duration lockDuration,
                                      @DefaultValue("24h") Duration maxLockDuration,
                                      @DefaultValue("1s") Duration delay,
                                      @DefaultValue("8s") Duration maxDelay,
                                      @DefaultValue("50") int maxDelayedRequests,
                                      @DefaultValue("true") boolean notifyByMail) {

    public LoginProtectionSettings {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "zs.identity.login-protection.max-attempts must be at least 1, was " + maxAttempts);
        }
        requirePositive(lockDuration, "lock-duration");
        requirePositive(maxLockDuration, "max-lock-duration");
        if (maxLockDuration.compareTo(lockDuration) < 0) {
            throw new IllegalArgumentException(
                    "zs.identity.login-protection.max-lock-duration must not be shorter than lock-duration");
        }
        if (delay.isNegative() || maxDelay.isNegative()) {
            throw new IllegalArgumentException("zs.identity.login-protection.delay and max-delay must not be negative");
        }
        if (maxDelayedRequests < 1) {
            throw new IllegalArgumentException(
                    "zs.identity.login-protection.max-delayed-requests must be at least 1, was " + maxDelayedRequests);
        }
    }

    /** Defaults for tests that build the record by hand: switched on, as without configuration. */
    public static LoginProtectionSettings defaults() {
        return new LoginProtectionSettings(true, 3, Duration.ofMinutes(15), Duration.ofHours(24),
                Duration.ofSeconds(1), Duration.ofSeconds(8), 50, true);
    }

    /** The same settings switched on or off; see {@code IdentityProperties#withLoginProtection}. */
    public LoginProtectionSettings enabled(boolean newEnabled) {
        return new LoginProtectionSettings(newEnabled, maxAttempts, lockDuration, maxLockDuration, delay, maxDelay,
                maxDelayedRequests, notifyByMail);
    }

    /**
     * How long the lock lasts that starts with this failure.
     *
     * @param failures wrong passwords in a row, the one that starts the lock
     *                 included; a multiple of {@link #maxAttempts()}
     */
    public Duration lockDurationAfter(int failures) {
        int lockNumber = Math.max(1, failures / maxAttempts);
        return doubled(lockDuration, lockNumber - 1, maxLockDuration);
    }

    /**
     * How long a sign-in waits before its password is even looked at, after
     * this many failures.
     */
    public Duration delayAfter(int failures) {
        if (failures <= 0 || delay.isZero()) {
            return Duration.ZERO;
        }
        return doubled(delay, failures - 1, maxDelay);
    }

    /**
     * {@code base * 2^times}, but never above {@code cap}. Doubling step by
     * step rather than shifting: after a few dozen failures a shift would
     * overflow, and the cap is reached long before that anyway.
     */
    private static Duration doubled(Duration base, int times, Duration cap) {
        Duration result = base;
        for (int i = 0; i < times && result.compareTo(cap) < 0; i++) {
            result = result.multipliedBy(2);
        }
        return result.compareTo(cap) > 0 ? cap : result;
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("zs.identity.login-protection." + name + " must be positive");
        }
    }
}
