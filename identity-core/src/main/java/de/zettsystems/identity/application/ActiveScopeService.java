package de.zettsystems.identity.application;

import de.zettsystems.identity.values.Scope;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.Optional;

/**
 * The scope the signed-in person is currently working in: "I am at club 17
 * now".
 *
 * <p>After the switch the roles of that scope apply unqualified:
 * {@code @RolesAllowed("ADMIN")} then means "admin of club 17". The switch
 * does not change the assignments themselves; it only changes which of them
 * apply without a suffix (see {@link IdentityUserDetails}).
 *
 * <p><strong>No scope is active at sign-in.</strong> Only the application
 * knows which one it should be: it knows the last tenant visited, the address
 * bar, or the selection on its start page. "Just take the only one
 * automatically" would be convenient and dangerous for exactly that reason --
 * a second membership would silently turn into different behaviour.
 *
 * <p>A scope in which the person holds no role is allowed and simply yields no
 * additional permissions, so the check falls back on the global roles.
 */
public class ActiveScopeService {

    private static final Logger LOG = LoggerFactory.getLogger(ActiveScopeService.class);

    /** Set in tests only; in production the repository is created when saving. */
    private final @Nullable SecurityContextRepository contextRepository;

    public ActiveScopeService() {
        this(null);
    }

    ActiveScopeService(@Nullable SecurityContextRepository contextRepository) {
        this.contextRepository = contextRepository;
    }

    /** The active scope, or empty, which is also the answer without a sign-in. */
    public Optional<Scope> current() {
        return currentUser().flatMap(IdentityUserDetails::activeScope);
    }

    /**
     * Switches the active scope of the running session. {@code null} gives it
     * up, after which only the global roles apply.
     *
     * <p>Without a sign-in -- or when the application uses a principal of its
     * own -- nothing happens. An exception would be wrong here: the switch is
     * a convenience, not a security decision, and the qualified permissions
     * apply regardless of it.
     */
    public void switchTo(@Nullable Scope scope) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        Optional<IdentityUserDetails> user = currentUser();
        if (current == null || user.isEmpty()) {
            LOG.debug("No identity principal in the current session; active scope unchanged");
            return;
        }

        IdentityUserDetails updated = user.get().withActiveScope(scope);
        SecurityContexts.replace(UsernamePasswordAuthenticationToken.authenticated(
                updated, current.getCredentials(), updated.getAuthorities()), contextRepository);
        LOG.debug("Active scope switched to {}", scope);
    }

    private static Optional<IdentityUserDetails> currentUser() {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null || !current.isAuthenticated()
                || !(current.getPrincipal() instanceof IdentityUserDetails user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }
}
