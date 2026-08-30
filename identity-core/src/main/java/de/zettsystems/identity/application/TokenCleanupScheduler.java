package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * Räumt täglich abgelaufene und eingelöste Token ab.
 *
 * <p>Ohne diesen Lauf wüchse {@code auth_token} mit jeder Registrierung und
 * jedem Reset — nichts davon wird je wieder gebraucht. Abgelaufene Token
 * bleiben noch {@value #GRACE_DAYS} Tage liegen, damit ein alter Link als
 * „abgelaufen" statt als „unbekannt" gemeldet werden kann.
 *
 * <p>Läuft nur, wenn die Anwendung {@code @EnableScheduling} setzt — der
 * Baustein schaltet es nicht selbst ein — und lässt sich mit
 * {@code zs.identity.token-cleanup.enabled=false} abschalten.
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

    /** Nachts um 03:15 — nach dem Backup-Fenster üblicher Betriebsumgebungen. */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public int cleanUp() {
        int removed = tokens.deleteObsolete(clock.instant().minus(Duration.ofDays(GRACE_DAYS)));
        if (removed > 0) {
            LOG.info("{} abgelaufene oder eingelöste Token entfernt.", removed);
        }
        return removed;
    }
}
