package de.zettsystems.identity.application;

import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityMailHealthIndicatorTest {

    private static final Instant T0 = Instant.parse("2026-11-02T09:30:00Z");
    private static final Duration INTERVAL = Duration.ofHours(3);

    private final AtomicInteger logins = new AtomicInteger();
    private final Deque<Runnable> background = new ArrayDeque<>();
    private final MovableClock clock = new MovableClock();
    private boolean serverDown;

    private final IdentityMailHealthIndicator indicator = new IdentityMailHealthIndicator(
            this::login, INTERVAL, clock, background::add);

    private void login() throws MessagingException {
        logins.incrementAndGet();
        if (serverDown) {
            throw new MessagingException("454 Temporary authentication failure");
        }
    }

    private void runBackground() {
        while (!background.isEmpty()) {
            background.poll().run();
        }
    }

    @Test
    void theFirstReadAnswersAtOnceAndChecksInTheBackground() {
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(logins).hasValue(0);
        assertThat(background).hasSize(1);

        runBackground();

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void manyReadsStartOneCheck() {
        indicator.health();
        indicator.health();
        indicator.health();
        assertThat(background).hasSize(1);
    }

    @Test
    void aFreshResultNeedsNoLogin() {
        indicator.probe();
        clock.advance(INTERVAL.minusSeconds(1));

        indicator.health();

        assertThat(background).isEmpty();
        assertThat(logins).hasValue(1);
    }

    @Test
    void anOldResultIsCheckedAgain() {
        indicator.probe();
        clock.advance(INTERVAL);

        indicator.health();
        runBackground();

        assertThat(logins).hasValue(2);
    }

    @Test
    void aDeadServerDegradesInsteadOfGoingDown() {
        serverDown = true;
        indicator.probe();

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(IdentityMailHealthIndicator.DEGRADED);
        assertThat(health.getDetails()).containsEntry("error", "454 Temporary authentication failure");
    }

    @Test
    void theStatusItselfDoesNotTellThePublicWhatIsWrong() {
        assertThat(IdentityMailHealthIndicator.DEGRADED.getDescription()).isEmpty();
    }

    @Test
    void keepsTheStartOfTheOutageAcrossFailedChecks() {
        serverDown = true;
        indicator.probe();
        clock.advance(INTERVAL);
        indicator.probe();

        assertThat(indicator.health().getDetails())
                .containsEntry("since", T0.toString())
                .containsEntry("checked", T0.plus(INTERVAL).toString());
    }

    @Test
    void anAcceptedMailEndsTheOutageWithoutALogin() {
        serverDown = true;
        indicator.probe();

        indicator.delivered();

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(logins).hasValue(1);
    }

    /** A rejected address fails the same way, so a failure is a reason to look, not a verdict. */
    @Test
    void aFailedDeliveryChecksTheServerInsteadOfAssumingItIsGone() {
        indicator.delivered();

        indicator.failed();
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(background).hasSize(1);

        runBackground();
        assertThat(logins).hasValue(1);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void aCheckCanRunAgainAfterTheLastOneFinished() {
        indicator.failed();
        runBackground();
        indicator.failed();
        assertThat(background).hasSize(1);
    }

    private static final class MovableClock extends Clock {

        private Instant now = T0;

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
