package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.UserAccount;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * The password sign-in with a brake against guessing (issue #2): Spring
 * Security's own {@link DaoAuthenticationProvider}, with a delay in front of
 * it and a temporary lock behind it.
 *
 * <p>A bean of type {@link AuthenticationProvider} is what Spring Security
 * takes for its global {@code AuthenticationManager} instead of building a
 * {@code DaoAuthenticationProvider} from the {@code UserDetailsService} --
 * so the form login of every application gets this without a line of
 * configuration. What it does, in order:
 *
 * <ol>
 *   <li>Waits out the delay the sign-in name and the client address owe
 *       ({@link LoginThrottle}), or turns the attempt down unchecked when
 *       too many are waiting already.</li>
 *   <li>Checks the password through the {@code DaoAuthenticationProvider}.
 *       A wrong one is counted, in memory and on the account.</li>
 *   <li>Only then asks whether the account is locked. A locked account fails
 *       exactly like a wrong password -- the same exception, so the same
 *       message, and the same bcrypt comparison behind it, so the same time.
 *       Checking before the password would answer a locked account faster,
 *       and a different message would tell whoever is guessing that the
 *       address has an account, or that the password they just tried was
 *       right.</li>
 *   <li>A sign-in that gets through forgets the failures of the account.</li>
 * </ol>
 *
 * <p>An unknown address costs the same bcrypt comparison as well: the
 * {@code DaoAuthenticationProvider} compares against a dummy hash for it.
 *
 * <p>Passkeys go through a provider of their own and are not counted: there
 * is nothing to guess. A locked account may still sign in with one -- the
 * lock is there to stop guessing at the password, and a passkey proves
 * possession more strongly than the link in a reset mail, which lifts the
 * lock too. Such a sign-in forgets the failures like a password sign-in
 * ({@link #onAuthenticationSuccess}).
 */
final class LoginProtectionAuthenticationProvider implements AuthenticationProvider {

    private static final Logger LOG = LoggerFactory.getLogger(LoginProtectionAuthenticationProvider.class);

    /** The text the {@code DaoAuthenticationProvider} uses for a wrong password; ours must not differ. */
    static final String BAD_CREDENTIALS = "Bad credentials";

    private final DaoAuthenticationProvider passwordCheck;
    private final LoginThrottle throttle;
    private final LoginAttempts attempts;

    LoginProtectionAuthenticationProvider(DaoAuthenticationProvider passwordCheck, LoginThrottle throttle,
                                          LoginAttempts attempts) {
        this.passwordCheck = passwordCheck;
        this.throttle = throttle;
        this.attempts = attempts;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String name = UserAccount.normalizeEmail(authentication.getName());
        String client = clientAddressOf(authentication);
        if (!throttle.awaitTurn(name, client)) {
            LOG.debug("Sign-in for {} from {} turned down: too many sign-ins waiting", name, client);
            throw new BadCredentialsException(BAD_CREDENTIALS);
        }
        Authentication result;
        try {
            result = passwordCheck.authenticate(authentication);
        } catch (BadCredentialsException e) {
            recordFailure(name, client);
            throw e;
        }
        if (attempts.isLocked(name)) {
            // Counted in memory, so the delay keeps growing; the account
            // itself counts nothing while it is locked.
            throttle.recordFailure(name, client);
            throw new BadCredentialsException(BAD_CREDENTIALS);
        }
        recordSuccess(name);
        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /** A passkey sign-in counts as proof of possession: failures and lock go. */
    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication() instanceof PasskeyAuthentication passkey) {
            recordSuccess(UserAccount.normalizeEmail(passkey.getName()));
        }
    }

    private void recordFailure(String name, @Nullable String client) {
        throttle.recordFailure(name, client);
        try {
            attempts.recordFailure(name, client);
        } catch (RuntimeException e) {
            // The answer is "wrong password" either way; a database hiccup
            // while counting must not turn it into an error page.
            LOG.warn("Could not count a failed sign-in", e);
        }
    }

    private void recordSuccess(String name) {
        throttle.recordSuccess(name);
        try {
            attempts.recordSuccess(name);
        } catch (RuntimeException e) {
            LOG.warn("Could not reset the failed sign-ins of an account", e);
        }
    }

    /**
     * The address the form login recorded for the request. Behind a reverse
     * proxy that is the proxy's, unless the application lets Spring read the
     * forwarded headers ({@code server.forward-headers-strategy}) -- without
     * that, all clients share one address and one delay.
     */
    private static @Nullable String clientAddressOf(Authentication authentication) {
        return authentication.getDetails() instanceof WebAuthenticationDetails details
                ? details.getRemoteAddress()
                : null;
    }
}
