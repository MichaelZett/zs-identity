package de.zettsystems.identity.application;

import de.zettsystems.identity.values.EmailChanged;
import de.zettsystems.identity.values.ExternalIdentityUnlinked;
import de.zettsystems.identity.values.IdentityAccountEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Discards the remember-me tokens of an account as soon as something happens
 * that must lock the other devices out.
 *
 * <p>Applications keep people signed in for weeks with Spring Security's
 * remember-me ({@link PersistentTokenRepository}, one row per device). Whoever
 * changes or resets their password -- because a phone is gone -- expects that
 * phone to be out. Without this the cookie on it stays valid for its full
 * lifetime: the password it was issued under no longer exists, and the cookie
 * never asks for one. The same goes for an account that is locked or deleted,
 * and for a change of sign-in name, which leaves the rows pointing at a name
 * nobody uses any more.
 *
 * <p><strong>Nothing to do without remember-me.</strong> The repository comes
 * through an {@link ObjectProvider}: an application that keeps no tokens has no
 * such bean, and then this listener is a no-op. Switched off entirely with
 * {@code zs.identity.remember-me-cleanup.enabled=false} -- for an application
 * that wants to decide for itself, on the events of
 * {@link IdentityAccountEvent}.
 *
 * <p><strong>Only after the commit.</strong> A password change that is rolled
 * back afterwards must not have thrown anyone out; and conversely, tokens
 * removed inside the transaction would come back with it.
 *
 * <p><strong>The session of whoever made the change stays.</strong> It hangs
 * off the HTTP session, not off the cookie, and Spring only reaches for the
 * cookie once that session is gone. So the person who changes their password
 * carries on working and signs in again at the next visit -- which is the
 * point: it is the <em>other</em> devices that are meant to be out.
 */
class RememberMeTokenCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(RememberMeTokenCleaner.class);

    private final ObjectProvider<PersistentTokenRepository> tokenRepository;

    RememberMeTokenCleaner(ObjectProvider<PersistentTokenRepository> tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * {@code fallbackExecution} because the services are not the only callers:
     * a test or a background run without a transaction should clean up all the
     * same, instead of silently dropping the event.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAccountEvent(IdentityAccountEvent event) {
        // Tokens were issued for the name that was valid until now. After a
        // change of address that is the old one -- the new one cannot have any
        // yet, and cleaning it would be cleaning nothing.
        @Nullable String signInName = event instanceof EmailChanged changed
                ? changed.previousEmail()
                : event.email();
        discardTokens(signInName, event.getClass().getSimpleName());
    }

    /**
     * A provider unlinked is a way in gone, like a changed password: a device
     * that was signed in through it must not stay in through its cookie.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onExternalIdentityUnlinked(ExternalIdentityUnlinked event) {
        discardTokens(event.email(), event.getClass().getSimpleName());
    }

    private void discardTokens(@Nullable String signInName, String cause) {
        if (signInName == null) {
            return;
        }
        PersistentTokenRepository repository = tokenRepository.getIfAvailable();
        if (repository == null) {
            return;
        }
        try {
            repository.removeUserTokens(signInName);
            LOG.debug("Discarded the remember-me tokens of {} after {}", signInName, cause);
        } catch (RuntimeException e) {
            // The change itself is committed and did succeed. Letting the
            // exception through would present it to the person as a failure,
            // and they would do it again -- see the same reasoning in
            // AuthenticationRefresher.
            LOG.warn("Could not discard the remember-me tokens of an account after {}", cause, e);
        }
    }
}
