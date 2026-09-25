package de.zettsystems.identity.values;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginProtectionSettingsTest {

    private final LoginProtectionSettings defaults = LoginProtectionSettings.defaults();

    @Test
    void theDelayDoublesFromTheFirstFailureUpToTheMaximum() {
        assertThat(defaults.delayAfter(0)).isZero();
        assertThat(defaults.delayAfter(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(defaults.delayAfter(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(defaults.delayAfter(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(defaults.delayAfter(4)).isEqualTo(Duration.ofSeconds(8));
        assertThat(defaults.delayAfter(5)).isEqualTo(Duration.ofSeconds(8));
        assertThat(defaults.delayAfter(Integer.MAX_VALUE)).as("no overflow").isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void aZeroDelaySwitchesTheDelayOff() {
        LoginProtectionSettings noDelay = new LoginProtectionSettings(true, 3, Duration.ofMinutes(15),
                Duration.ofHours(24), Duration.ZERO, Duration.ofSeconds(8), 50, true);

        assertThat(noDelay.delayAfter(10)).isZero();
    }

    @Test
    void eachLockLastsTwiceAsLongAsTheOneBeforeUpToTheMaximum() {
        assertThat(defaults.lockDurationAfter(3)).isEqualTo(Duration.ofMinutes(15));
        assertThat(defaults.lockDurationAfter(6)).isEqualTo(Duration.ofMinutes(30));
        assertThat(defaults.lockDurationAfter(9)).isEqualTo(Duration.ofHours(1));
        assertThat(defaults.lockDurationAfter(3 * 30)).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void nonsenseIsRefusedAtStartup() {
        assertThatThrownBy(() -> new LoginProtectionSettings(true, 0, Duration.ofMinutes(15), Duration.ofHours(24),
                Duration.ofSeconds(1), Duration.ofSeconds(8), 50, true))
                .hasMessageContaining("max-attempts");
        assertThatThrownBy(() -> new LoginProtectionSettings(true, 3, Duration.ZERO, Duration.ofHours(24),
                Duration.ofSeconds(1), Duration.ofSeconds(8), 50, true))
                .hasMessageContaining("lock-duration");
        assertThatThrownBy(() -> new LoginProtectionSettings(true, 3, Duration.ofHours(2), Duration.ofHours(1),
                Duration.ofSeconds(1), Duration.ofSeconds(8), 50, true))
                .hasMessageContaining("max-lock-duration");
        assertThatThrownBy(() -> new LoginProtectionSettings(true, 3, Duration.ofMinutes(15), Duration.ofHours(24),
                Duration.ofSeconds(-1), Duration.ofSeconds(8), 50, true))
                .hasMessageContaining("delay");
        assertThatThrownBy(() -> new LoginProtectionSettings(true, 3, Duration.ofMinutes(15), Duration.ofHours(24),
                Duration.ofSeconds(1), Duration.ofSeconds(8), 0, true))
                .hasMessageContaining("max-delayed-requests");
    }

    @Test
    void switchingOffKeepsTheRest() {
        LoginProtectionSettings off = defaults.enabled(false);

        assertThat(off.enabled()).isFalse();
        assertThat(off.maxAttempts()).isEqualTo(defaults.maxAttempts());
    }
}
