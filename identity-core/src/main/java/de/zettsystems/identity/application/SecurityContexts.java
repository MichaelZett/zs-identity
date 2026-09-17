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
 * Installs a changed authentication so that the running session keeps it.
 *
 * <p>Two places need this: the {@link AuthenticationRefresher} after a change
 * of roles, and the {@link ActiveScopeService} when the active scope is
 * switched. Both would otherwise walk into the same trap -- since Spring
 * Security 6 the framework no longer writes the {@code SecurityContext} back
 * into the session by itself, so the change would last exactly one request.
 */
final class SecurityContexts {

    private SecurityContexts() {
        // Utility class
    }

    /** Replaces the authentication in the current thread and in the session. */
    static void replace(Authentication updated, @Nullable SecurityContextRepository contextRepository) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(updated);
        SecurityContextHolder.setContext(context);
        saveToSession(context, contextRepository);
    }

    /**
     * Without a servlet environment (tests, background runs) there is no
     * session to write anything into, and the changed context in the current
     * thread is all there is.
     *
     * <p>The repository is deliberately created here and not in the
     * constructor of a service: it drags in the servlet types, which this
     * building block only knows {@code compileOnly}. An application without a
     * servlet environment could otherwise not even create the bean.
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
