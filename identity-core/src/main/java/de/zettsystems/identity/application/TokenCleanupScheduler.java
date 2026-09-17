package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * Clears out expired and redeemed tokens once a day.
 *
 * <p>Without this run, {@code auth_token} would grow with every registration
 * and every reset, none of which is ever needed again. Expired tokens are kept
 * for another {@value #GRACE_DAYS} days, so that an old link can be reported
 * as "expired" rather than as "unknown".
 *
 * <p>It runs only when the application sets {@code @EnableScheduling} -- the
 * building block does not turn that on itself -- and can be switched off with
 * {@code zs.identity.token-cleanup.enabled=false}.
 */
public class TokenCleanupScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(TokenCleanupScheduler.class);

    static final int GRACE_DAYS = 7;

    private final AuthTokenRepository tokens;
    private final Clock clock;

    TokenCleanupScheduler(AuthTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /** At 03:15 at night, after the backup window of typical environments. */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public int cleanUp() {
        int removed = tokens.deleteObsolete(clock.instant().minus(Duration.ofDays(GRACE_DAYS)));
        if (removed > 0) {
            LOG.info("Removed {} expired or redeemed tokens.", removed);
        }
        return removed;
    }
}
