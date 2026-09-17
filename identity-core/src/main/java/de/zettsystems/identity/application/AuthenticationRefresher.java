package de.zettsystems.identity.application;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Refreshes the authorities of the <em>running</em> session after the roles of
 * its account have changed.
 *
 * <p>Without this, Spring Security holds on to the authorities that applied at
 * sign-in time. Someone who gains a role within the same session -- by
 * creating their first group and thereby becoming its leader, say -- walks
 * into every role-protected view afterwards and gets "access denied" until
 * they sign in again. That looks like a bug and is not one, but nobody can
 * know that.
 *
 * <p>The refresher only acts when the change concerns <strong>the account that
 * is currently signed in</strong>. If an administrator grants a role to
 * somebody else, nothing happens here: that person's session is out of reach,
 * and they get the role at their next sign-in.
 *
 * <p>Two subtleties that are easily overlooked:
 *
 * <ul>
 *   <li><strong>Only after the commit.</strong> If the session were refreshed
 *       inside the transaction and that transaction were then rolled back, the
 *       person would carry on with rights that never existed in the
 *       database.</li>
 *   <li><strong>The context has to be saved.</strong> Since Spring Security 6
 *       the framework no longer writes the {@code SecurityContext} back into
 *       the session by itself. Without saving it explicitly the refresh would
 *       last exactly one request.</li>
 * </ul>
 */
public class AuthenticationRefresher {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationRefresher.class);

    private final UserDetailsService userDetailsService;
    /** Set in tests only; in production the repository is created when saving. */
    private final @Nullable SecurityContextRepository contextRepository;

    public AuthenticationRefresher(UserDetailsService userDetailsService) {
        this(userDetailsService, null);
    }

    AuthenticationRefresher(UserDetailsService userDetailsService,
                            @Nullable SecurityContextRepository contextRepository) {
        this.userDetailsService = userDetailsService;
        this.contextRepository = contextRepository;
    }

    /**
     * Refreshes the session as soon as the running transaction is committed,
     * provided {@code email} is the account that is signed in.
     *
     * @param email the address of the account whose roles have changed;
     *              {@code null} for managed accounts, which cannot sign in
     *              anyway
     */
    public void refreshAfterCommit(@Nullable String email) {
        if (email == null || !isCurrentUser(email)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Without a running transaction the change is already written.
            refresh(email);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                refresh(email);
            }
        });
    }

    private static boolean isCurrentUser(String email) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        return current != null
                && current.isAuthenticated()
                && email.equalsIgnoreCase(current.getName());
    }

    private void refresh(String email) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null) {
            return;
        }
        try {
            UserDetails fresh = userDetailsService.loadUserByUsername(email);
            Authentication updated = UsernamePasswordAuthenticationToken.authenticated(
                    fresh, current.getCredentials(), fresh.getAuthorities());

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(updated);
            SecurityContextHolder.setContext(context);
            saveToSession(context);

            LOG.debug("Refreshed the authorities of the running session: {}",
                    fresh.getAuthorities());
        } catch (UsernameNotFoundException e) {
            // The account has disappeared in the meantime, or has no
            // credentials any more. Leaving the old session in place is right
            // here: the next protected call fails anyway, and an exception
            // would make the business action that just succeeded look like a
            // failure after the fact.
            LOG.debug("No account to refresh for {}", email, e);
        }
    }

    private void saveToSession(SecurityContext context) {
        SecurityContexts.saveToSession(context, contextRepository);
    }
}
