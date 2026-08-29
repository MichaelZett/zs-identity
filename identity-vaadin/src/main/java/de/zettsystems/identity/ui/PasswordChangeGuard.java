package de.zettsystems.identity.ui;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterListener;
import de.zettsystems.identity.application.IdentityUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.Serial;

/**
 * Führt ein Konto, das sein Passwort ändern muss, auf die
 * {@link ChangePasswordView} — bei <em>jeder</em> Navigation, außer auf diese
 * Ansicht selbst.
 *
 * <p>Gelesen wird das Flag aus dem {@code SecurityContext}
 * ({@link IdentityUserDetails#mustChangePassword()}), nicht aus der Datenbank:
 * Es hängt an jedem Seitenaufruf. Nach dem Wechsel frischt der Dienst die
 * Sitzung auf, damit das Flag dort verschwindet.
 *
 * <p>Angehängt wird der Wächter je {@code UI} durch den
 * {@link PasswordChangeServiceInitListener}; Tests hängen ihn selbst an.
 */
public final class PasswordChangeGuard implements BeforeEnterListener {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Über den Pfad, nicht über die Klasse: So hängt der Wächter nicht an
        // der Registrierung der Ansicht (Tests registrieren ein leeres Ziel).
        if (IdentityRoutes.CHANGE_PASSWORD.equals(event.getLocation().getPath())) {
            return;
        }
        if (mustChangePassword()) {
            event.forwardTo(IdentityRoutes.CHANGE_PASSWORD);
        }
    }

    /** Ob das angemeldete Konto sein Passwort ändern muss — {@code false} ohne Anmeldung. */
    public static boolean mustChangePassword() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof IdentityUserDetails user
                && user.mustChangePassword();
    }
}
