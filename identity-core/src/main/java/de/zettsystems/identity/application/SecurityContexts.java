package de.zettsystems.identity.application;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Setzt eine geänderte Anmeldung so, dass die laufende Sitzung sie behält.
 *
 * <p>Zwei Stellen brauchen das: Der {@link AuthenticationRefresher} nach einer
 * Rollenänderung und der {@link ActiveScopeService} beim Wechsel des aktiven
 * Bereichs. Beide stolpern sonst über dieselbe Falle — seit Spring Security 6
 * schreibt der Rahmen den {@code SecurityContext} nicht mehr von sich aus in
 * die Sitzung zurück, die Änderung hielte also genau einen Aufruf lang.
 */
final class SecurityContexts {

    private SecurityContexts() {
        // Hilfsklasse
    }

    /** Ersetzt die Anmeldung im aktuellen Thread und in der Sitzung. */
    static void replace(Authentication updated, @Nullable SecurityContextRepository contextRepository) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(updated);
        SecurityContextHolder.setContext(context);
        saveToSession(context, contextRepository);
    }

    /**
     * Ohne Servlet-Umgebung (Tests, Hintergrundläufe) gibt es keine Sitzung, in
     * die sich etwas schreiben ließe — dann bleibt es beim geänderten Kontext
     * im aktuellen Thread.
     *
     * <p>Das Repository entsteht bewusst erst hier und nicht im Konstruktor
     * eines Dienstes: Es zieht die Servlet-Typen nach sich, die der Baustein
     * nur {@code compileOnly} kennt. Eine Anwendung ohne Servlet-Umgebung
     * könnte die Bean sonst gar nicht erst erzeugen.
     */
    static void saveToSession(SecurityContext context, @Nullable SecurityContextRepository contextRepository) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        if (response == null) {
            return;
        }
        SecurityContextRepository repository = contextRepository != null
                ? contextRepository
                : new HttpSessionSecurityContextRepository();
        repository.saveContext(context, request, response);
    }
}
