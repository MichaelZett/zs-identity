package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.domain.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Hält fest, wann sich jemand zuletzt angemeldet hat.
 *
 * <p>Über ein Spring-Security-Ereignis und nicht im {@code UserDetailsService}:
 * Der wird auch bei einem gescheiterten Anmeldeversuch aufgerufen, das wäre
 * also kein Login-Zeitpunkt. {@link AuthenticationSuccessEvent} feuert nur nach
 * erfolgreicher Prüfung.
 */
class LoginRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(LoginRecorder.class);

    private final UserAccountRepository userRepository;
    private final Clock clock;

    LoginRecorder(UserAccountRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        userRepository.findByEmail(UserAccount.normalizeEmail(username))
                .ifPresentOrElse(user -> user.recordLogin(clock.instant()),
                        () -> LOG.debug("Successful authentication for {} without a matching account", username));
    }
}
