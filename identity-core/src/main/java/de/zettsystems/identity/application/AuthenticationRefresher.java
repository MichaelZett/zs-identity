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
 * Frischt die Berechtigungen der <em>laufenden</em> Sitzung auf, nachdem sich
 * die Rollen ihres Kontos geändert haben.
 *
 * <p>Ohne das hält Spring Security die Berechtigungen fest, die beim Anmelden
 * galten. Wer sich in derselben Sitzung eine Rolle erwirbt — etwa indem er
 * seine erste Gruppe anlegt und damit deren Leitung wird —, läuft danach in
 * jede rollengeschützte Ansicht hinein und bekommt „Zugriff verweigert", bis
 * er sich neu anmeldet. Das sieht nach einem Fehler aus und ist keiner, aber
 * niemand kann das wissen.
 *
 * <p>Der Auffrischer greift nur, wenn die Änderung <strong>das gerade
 * angemeldete Konto</strong> betrifft. Vergibt eine Administration jemand
 * anderem eine Rolle, passiert hier nichts — deren Sitzung ist nicht
 * erreichbar, und sie bekommt die Rolle beim nächsten Anmelden.
 *
 * <p>Zwei Feinheiten, die leicht übersehen werden:
 *
 * <ul>
 *   <li><strong>Erst nach dem Commit.</strong> Würde die Sitzung schon
 *       innerhalb der Transaktion aufgefrischt und diese danach
 *       zurückgerollt, liefe die Person mit Rechten weiter, die es in der
 *       Datenbank nie gab.</li>
 *   <li><strong>Der Kontext muss gespeichert werden.</strong> Seit Spring
 *       Security 6 schreibt der Rahmen den {@code SecurityContext} nicht mehr
 *       von sich aus in die Sitzung zurück. Ohne das ausdrückliche Speichern
 *       hielte die Auffrischung genau einen Aufruf lang.</li>
 * </ul>
 */
public class AuthenticationRefresher {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationRefresher.class);

    private final UserDetailsService userDetailsService;
    /** Nur für Tests gesetzt; im Betrieb entsteht das Repository beim Speichern. */
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
     * Frischt die Sitzung auf, sobald die laufende Transaktion festgeschrieben
     * ist — sofern {@code email} das angemeldete Konto ist.
     *
     * @param email die Adresse des Kontos, dessen Rollen sich geändert haben;
     *              {@code null} bei verwalteten Konten, die sich ohnehin nicht
     *              anmelden können
     */
    public void refreshAfterCommit(@Nullable String email) {
        if (email == null || !isCurrentUser(email)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Ohne laufende Transaktion ist die Änderung bereits geschrieben.
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

            LOG.debug("Berechtigungen der laufenden Sitzung aufgefrischt: {}",
                    fresh.getAuthorities());
        } catch (UsernameNotFoundException e) {
            // Das Konto ist zwischenzeitlich verschwunden oder hat keine
            // Anmeldedaten mehr. Die alte Sitzung stehen zu lassen ist hier
            // richtig: Der nächste geschützte Aufruf scheitert ohnehin, und
            // eine Ausnahme würde die gerade erfolgreiche Fachaktion
            // nachträglich als Fehler erscheinen lassen.
            LOG.debug("Kein Konto zum Auffrischen für {}", email, e);
        }
    }

    private void saveToSession(SecurityContext context) {
        SecurityContexts.saveToSession(context, contextRepository);
    }
}
