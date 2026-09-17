package de.zettsystems.identity.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A clock that can be moved forward in a test.
 *
 * <p>This makes it possible to check that a token stops being valid once it has
 * expired, without waiting 24 hours in the test. That is exactly why the clock
 * hangs in the context as a bean instead of being called through
 * {@code Instant.now()} in production code.
 */
public class MutableTestClock extends Clock {

    private static final Instant START = Instant.parse("2026-09-01T10:00:00Z");

    private Instant now = START;

    @Override
    public ZoneId getZone() {
        return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }

    public void advanceBy(Duration duration) {
        this.now = this.now.plus(duration);
    }

    public void reset() {
        this.now = START;
    }
}
