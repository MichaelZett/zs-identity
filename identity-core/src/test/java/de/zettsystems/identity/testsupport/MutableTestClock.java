package de.zettsystems.identity.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Uhr, die im Test vorgestellt werden kann.
 *
 * <p>Damit lässt sich prüfen, dass ein Token nach Ablauf nicht mehr gilt, ohne
 * im Test 24 Stunden zu warten. Genau dafür hängt die Uhr als Bean im Kontext
 * und wird nicht per {@code Instant.now()} im Produktivcode aufgerufen.
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
