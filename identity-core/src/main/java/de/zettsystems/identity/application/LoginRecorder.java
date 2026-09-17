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
 * Records when someone last signed in.
 *
 * <p>Through a Spring Security event rather than in the
 * {@code UserDetailsService}: that one is called for a failed sign-in attempt
 * too, so it would not be a sign-in time at all.
 * {@link AuthenticationSuccessEvent} fires only after a successful check.
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
