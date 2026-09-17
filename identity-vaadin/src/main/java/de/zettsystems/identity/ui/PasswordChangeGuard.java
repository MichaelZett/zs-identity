package de.zettsystems.identity.ui;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterListener;
import de.zettsystems.identity.application.IdentityUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.Serial;

/**
 * Sends an account that has to change its password to the
 * {@link ChangePasswordView}, on <em>every</em> navigation except to that view
 * itself.
 *
 * <p>The flag is read from the {@code SecurityContext}
 * ({@link IdentityUserDetails#mustChangePassword()}) and not from the database,
 * because it is consulted on every page load. After the change the service
 * refreshes the session so that the flag disappears there too.
 *
 * <p>The guard is attached per {@code UI} by the
 * {@link PasswordChangeServiceInitListener}; tests attach it themselves.
 */
public final class PasswordChangeGuard implements BeforeEnterListener {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // By path rather than by class: that way the guard does not depend on
        // the view being registered (tests register an empty target).
        if (IdentityRoutes.CHANGE_PASSWORD.equals(event.getLocation().getPath())) {
            return;
        }
        if (mustChangePassword()) {
            event.forwardTo(IdentityRoutes.CHANGE_PASSWORD);
        }
    }

    /** Whether the signed-in account has to change its password; {@code false} without a sign-in. */
    public static boolean mustChangePassword() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof IdentityUserDetails user
                && user.mustChangePassword();
    }
}
