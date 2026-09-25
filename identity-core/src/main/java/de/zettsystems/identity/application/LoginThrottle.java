package de.zettsystems.identity.application;

import de.zettsystems.identity.values.LoginProtectionSettings;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * The delay half of the protection against guessing: a sign-in waits before
 * its password is looked at, the longer the more has failed before it, from
 * the same sign-in name or from the same client address.
 *
 * <p><strong>Before, not after.</strong> A delay on the failed answer alone
 * would slow down nobody who simply stops waiting for it and sends the next
 * guess. Waiting before the check means every guess costs its time.
 *
 * <p><strong>By name, not by account.</strong> The failures are kept for
 * whatever was typed, whether an account exists for it or not. Otherwise an
 * unknown address would answer faster than a known one on the second try,
 * and the delay itself would tell which addresses have an account.
 *
 * <p><strong>In memory and bounded.</strong> Nothing here is worth a table:
 * it is forgotten once {@code lock-duration} has passed without a failure,
 * and each of the two maps keeps at most {@value #MAX_ENTRIES} entries,
 * dropping the one used longest ago. They are separate, so that trying many
 * addresses from one client cannot push that client's own entry out. In a
 * cluster each node counts for itself, which is the accepted price of no
 * table; the lock on the account is what holds across nodes.
 *
 * <p><strong>No unbounded parking.</strong> A waiting request holds a
 * servlet thread. At most {@code max-delayed-requests} wait at a time; the
 * next one is turned down without its password being checked at all.
 */
final class LoginThrottle {

    static final int MAX_ENTRIES = 10_000;

    /** How the waiting happens; a seam so that a test does not sit through seconds. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final LoginProtectionSettings settings;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Semaphore waiting;
    private final FailureCounts byName = new FailureCounts();
    private final FailureCounts byClient = new FailureCounts();

    LoginThrottle(LoginProtectionSettings settings, Clock clock, Sleeper sleeper) {
        this.settings = settings;
        this.clock = clock;
        this.sleeper = sleeper;
        this.waiting = new Semaphore(settings.maxDelayedRequests());
    }

    LoginThrottle(LoginProtectionSettings settings, Clock clock) {
        this(settings, clock, duration -> Thread.sleep(duration));
    }

    /**
     * Waits out the delay this attempt owes.
     *
     * @return {@code false} if the attempt is to be turned down unchecked,
     *         because too many are waiting already or the thread was
     *         interrupted while it waited
     */
    boolean awaitTurn(String name, @Nullable String client) {
        Duration delay = delayFor(name, client);
        if (delay.isZero()) {
            return true;
        }
        if (!waiting.tryAcquire()) {
            return false;
        }
        try {
            sleeper.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            waiting.release();
        }
    }

    /** The longer of the two delays owed by this name and this client. */
    Duration delayFor(String name, @Nullable String client) {
        Instant now = clock.instant();
        int failures = byName.get(name, now);
        if (client != null) {
            failures = Math.max(failures, byClient.get(client, now));
        }
        return settings.delayAfter(failures);
    }

    void recordFailure(String name, @Nullable String client) {
        Instant now = clock.instant();
        byName.increment(name, now);
        if (client != null) {
            byClient.increment(client, now);
        }
    }

    /**
     * Forgets the failures of this name. The client's stay: whoever holds one
     * valid account must not be able to wipe the slate for guessing at the
     * others by signing in with it in between.
     */
    void recordSuccess(String name) {
        byName.remove(name);
    }

    private record Entry(int failures, Instant last) {
    }

    /** Access order, and the eldest entry goes once there are more than {@value #MAX_ENTRIES}. */
    private static final class LeastRecentlyUsed extends LinkedHashMap<String, Entry> {

        @Serial
        private static final long serialVersionUID = 1L;

        LeastRecentlyUsed() {
            super(16, 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > MAX_ENTRIES;
        }
    }

    /** One of the two maps: failures per key, least recently used dropped first. */
    private final class FailureCounts {

        private final Map<String, Entry> entries = new LeastRecentlyUsed();

        synchronized int get(String key, Instant now) {
            Entry entry = entries.get(key);
            if (entry == null) {
                return 0;
            }
            if (isForgotten(entry, now)) {
                entries.remove(key);
                return 0;
            }
            return entry.failures();
        }

        synchronized void increment(String key, Instant now) {
            Entry entry = entries.get(key);
            int failures = entry == null || isForgotten(entry, now) ? 1 : entry.failures() + 1;
            entries.put(key, new Entry(failures, now));
        }

        synchronized void remove(String key) {
            entries.remove(key);
        }

        private boolean isForgotten(Entry entry, Instant now) {
            return entry.last().plus(settings.lockDuration()).isBefore(now);
        }
    }
}
