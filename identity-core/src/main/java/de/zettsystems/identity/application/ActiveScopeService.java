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
 * Der Bereich, in dem die angemeldete Person gerade arbeitet — „ich bin jetzt
 * bei Verein 17".
 *
 * <p>Nach dem Wechsel gelten die Rollen dieses Bereichs unqualifiziert:
 * {@code @RolesAllowed("ADMIN")} meint dann „Admin von Verein 17". Die
 * Zuweisungen selbst ändert der Wechsel nicht; er ändert nur, welche davon
 * ohne Zusatz gelten (siehe {@link IdentityUserDetails}).
 *
 * <p><strong>Beim Anmelden ist kein Bereich aktiv.</strong> Welcher es sein
 * soll, weiß nur die Anwendung: Sie kennt den letzten besuchten Mandanten,
 * die Adresszeile oder die Auswahl auf ihrer Startseite. „Automatisch den
 * einzigen nehmen" wäre bequem und genau deshalb gefährlich — aus einer
 * zweiten Mitgliedschaft würde stillschweigend ein anderes Verhalten.
 *
 * <p>Ein Bereich, in dem die Person keine Rolle hat, ist erlaubt und ergibt
 * schlicht keine zusätzlichen Berechtigungen; die Prüfung bleibt also bei den
 * globalen Rollen.
 */
public class ActiveScopeService {

    private static final Logger LOG = LoggerFactory.getLogger(ActiveScopeService.class);

    /** Nur für Tests gesetzt; im Betrieb entsteht das Repository beim Speichern. */
    private final @Nullable SecurityContextRepository contextRepository;

    public ActiveScopeService() {
        this(null);
    }

    ActiveScopeService(@Nullable SecurityContextRepository contextRepository) {
        this.contextRepository = contextRepository;
    }

    /** Der aktive Bereich, oder leer — auch ohne Anmeldung. */
    public Optional<Scope> current() {
        return currentUser().flatMap(IdentityUserDetails::activeScope);
    }

    /**
     * Wechselt den aktiven Bereich der laufenden Sitzung. {@code null} gibt
     * ihn auf; danach gelten nur noch die globalen Rollen.
     *
     * <p>Ohne Anmeldung — oder wenn die Anwendung einen eigenen Prinzipal
     * benutzt — passiert nichts. Eine Ausnahme wäre hier falsch: Der Wechsel
     * ist eine Bequemlichkeit, keine Sicherheitsentscheidung; die
     * qualifizierten Berechtigungen gelten unabhängig davon.
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
