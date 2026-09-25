package de.zettsystems.identity.application;

import de.zettsystems.identity.testsupport.MutableTestClock;
import de.zettsystems.identity.values.LoginProtectionSettings;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleTest {

    private static final String CLIENT = "203.0.113.7";

    private final MutableTestClock clock = new MutableTestClock();
    private final List<Duration> slept = new ArrayList<>();
    private final LoginThrottle throttle =
            new LoginThrottle(LoginProtectionSettings.defaults(), clock, slept::add);

    @Test
    void theFirstAttemptDoesNotWait() {
        assertThat(throttle.awaitTurn("anna@example.com", CLIENT)).isTrue();

        assertThat(slept).isEmpty();
    }

    @Test
    void everyFailureOfTheSameNameDoublesTheWait() {
        throttle.recordFailure("anna@example.com", null);
        throttle.awaitTurn("anna@example.com", null);
        throttle.recordFailure("anna@example.com", null);
        throttle.awaitTurn("anna@example.com", null);

        assertThat(slept).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    /** Trying one password on many addresses is slowed down too. */
    @Test
    void failuresFromOneClientSlowDownEveryNameItTries() {
        throttle.recordFailure("anna@example.com", CLIENT);
        throttle.recordFailure("bert@example.com", CLIENT);
        throttle.recordFailure("carla@example.com", CLIENT);

        assertThat(throttle.delayFor("dora@example.com", CLIENT)).isEqualTo(Duration.ofSeconds(4));
        assertThat(throttle.delayFor("dora@example.com", "198.51.100.1")).as("another client").isZero();
    }

    /** Kept by what was typed: whether an account exists makes no difference to the wait. */
    @Test
    void aNameWithoutAnAccountWaitsJustAsLong() {
        throttle.recordFailure("anna@example.com", null);
        throttle.recordFailure("nobody@example.com", null);

        assertThat(throttle.delayFor("nobody@example.com", null))
                .isEqualTo(throttle.delayFor("anna@example.com", null));
    }

    @Test
    void aSuccessfulSignInForgetsTheNameButNotTheClient() {
        throttle.recordFailure("anna@example.com", CLIENT);

        throttle.recordSuccess("anna@example.com");

        assertThat(throttle.delayFor("anna@example.com", null)).isZero();
        assertThat(throttle.delayFor("anna@example.com", CLIENT)).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void failuresAreForgottenAfterTheLockDuration() {
        throttle.recordFailure("anna@example.com", CLIENT);

        clock.advanceBy(Duration.ofMinutes(16));

        assertThat(throttle.delayFor("anna@example.com", CLIENT)).isZero();
        throttle.recordFailure("anna@example.com", CLIENT);
        assertThat(throttle.delayFor("anna@example.com", CLIENT)).as("counting starts over").isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void theMemoryIsBounded() {
        for (int i = 0; i <= LoginThrottle.MAX_ENTRIES; i++) {
            throttle.recordFailure("user" + i + "@example.com", null);
        }

        assertThat(throttle.delayFor("user0@example.com", null)).as("the oldest entry made room").isZero();
        assertThat(throttle.delayFor("user1@example.com", null)).isEqualTo(Duration.ofSeconds(1));
    }

    /** One waiting request at most here; the next is turned down without waiting. */
    @Test
    void beyondTheCapARequestIsTurnedDownInsteadOfParked() throws Exception {
        LoginProtectionSettings oneAtATime = new LoginProtectionSettings(true, 3, Duration.ofMinutes(15),
                Duration.ofHours(24), Duration.ofSeconds(1), Duration.ofSeconds(8), 1, true);
        CountDownLatch sleeping = new CountDownLatch(1);
        CountDownLatch wakeUp = new CountDownLatch(1);
        LoginThrottle capped = new LoginThrottle(oneAtATime, clock, duration -> {
            sleeping.countDown();
            wakeUp.await(5, TimeUnit.SECONDS);
        });
        capped.recordFailure("anna@example.com", CLIENT);
        AtomicBoolean firstGotThrough = new AtomicBoolean();
        Thread first = Thread.ofVirtual().start(() -> firstGotThrough.set(capped.awaitTurn("anna@example.com", CLIENT)));
        assertThat(sleeping.await(5, TimeUnit.SECONDS)).isTrue();

        boolean second = capped.awaitTurn("bert@example.com", CLIENT);

        wakeUp.countDown();
        first.join(5_000);
        assertThat(second).isFalse();
        assertThat(firstGotThrough).isTrue();
        assertThat(capped.awaitTurn("bert@example.com", CLIENT)).as("the slot is free again").isTrue();
    }

    @Test
    void anInterruptedWaitTurnsTheAttemptDown() {
        LoginThrottle interrupted = new LoginThrottle(LoginProtectionSettings.defaults(), clock, duration -> {
            throw new InterruptedException("shutdown");
        });
        interrupted.recordFailure("anna@example.com", null);

        assertThat(interrupted.awaitTurn("anna@example.com", null)).isFalse();
        assertThat(Thread.interrupted()).as("the flag is kept for whoever owns the thread").isTrue();
    }
}
