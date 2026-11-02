package de.zettsystems.identity.application;

import jakarta.mail.MessagingException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The health component {@code mail} for applications that switch it on with
 * {@code zs.identity.mail-health.enabled=true}: a missing mail server does not
 * stop an application, so it reports {@link #DEGRADED} rather than DOWN.
 *
 * <p>Differs from Spring Boot's indicator in two ways. It answers from the
 * last result and never talks to the mail server while serving a health
 * request: that endpoint is usually public and called every few seconds, and
 * would otherwise mean one login at the provider per call and up to the
 * server's timeout of waiting. And the mail server is needed rarely, so it is
 * checked rarely: when the last result is older than the interval, a health
 * request starts one check in the background. Real deliveries count as well
 * ({@link MailDeliveryListener}). An application that knows when it will need
 * the server calls {@link #probe()} shortly before.
 *
 * <p>Works without scheduling on purpose: a building block cannot rely on
 * {@code @EnableScheduling} in the application. With nobody asking for the
 * health, nobody logs in.
 *
 * <p>Only this class and its auto-configuration carry a health type, and they
 * are loaded only when the application has switched the feature on and has
 * the library.
 */
public final class IdentityMailHealthIndicator implements HealthIndicator, MailDeliveryListener {

    /**
     * Reachable with HTTP 200, but not everything works. Only counts towards
     * the overall status if the application lists it in
     * {@code management.endpoint.health.status.order}.
     */
    public static final Status DEGRADED = new Status("DEGRADED", "Mail server not reachable");

    private static final Logger LOG = LoggerFactory.getLogger(IdentityMailHealthIndicator.class);

    private final MailConnection connection;
    private final Duration interval;
    private final Clock clock;
    private final Executor background;
    private final AtomicBoolean probing = new AtomicBoolean();
    private volatile @Nullable Probe last;

    IdentityMailHealthIndicator(MailConnection connection, Duration interval, Clock clock, Executor background) {
        this.connection = connection;
        this.interval = interval;
        this.clock = clock;
        this.background = background;
    }

    /** The indicator for a mail sender from {@code spring.mail.*}. */
    public static IdentityMailHealthIndicator of(JavaMailSenderImpl sender, Duration interval) {
        return new IdentityMailHealthIndicator(sender::testConnection, interval, Clock.systemUTC(),
                task -> Thread.ofVirtual().name("identity-mail-health").start(task));
    }

    /** Logs in at the mail server now and waits for the answer. */
    public void probe() {
        try {
            connection.test();
            delivered();
        } catch (MessagingException | RuntimeException e) {
            markUnreachable(String.valueOf(e.getMessage()));
        }
    }

    @Override
    public void delivered() {
        Instant now = clock.instant();
        boolean wasFailing = outageStart() != null;
        last = new Probe(now, null, now);
        if (wasFailing) {
            LOG.info("Mail server reachable again");
        }
    }

    @Override
    public void failed() {
        probeInBackground();
    }

    @Override
    public Health health() {
        Probe probe = last;
        if (probe == null || !probe.at().plus(interval).isAfter(clock.instant())) {
            probeInBackground();
        }
        if (probe == null) {
            return Health.unknown().build();
        }
        if (probe.error() == null) {
            return Health.up().withDetail("checked", probe.at().toString()).build();
        }
        return Health.status(DEGRADED)
                .withDetail("checked", probe.at().toString())
                .withDetail("since", probe.since().toString())
                .withDetail("error", probe.error())
                .build();
    }

    private void probeInBackground() {
        if (probing.compareAndSet(false, true)) {
            background.execute(() -> {
                try {
                    probe();
                } finally {
                    probing.set(false);
                }
            });
        }
    }

    private void markUnreachable(String error) {
        Instant now = clock.instant();
        Instant start = outageStart();
        if (start == null) {
            LOG.warn("Mail server not reachable: {}", error);
            last = new Probe(now, error, now);
        } else {
            last = new Probe(now, error, start);
        }
    }

    private @Nullable Instant outageStart() {
        Probe probe = last;
        return probe != null && probe.error() != null ? probe.since() : null;
    }

    /** One login at the mail server. */
    @FunctionalInterface
    interface MailConnection {
        void test() throws MessagingException;
    }

    private record Probe(Instant at, @Nullable String error, Instant since) {
    }
}
