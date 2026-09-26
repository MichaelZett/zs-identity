package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.ExternalSignInException;
import de.zettsystems.identity.application.ExternalSignInException.Reason;
import de.zettsystems.identity.values.IdentityPaths;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;

/**
 * Where the browser goes after the round trip through a provider.
 *
 * <ul>
 *   <li>Signed in: where the form login would send it -- the page that
 *       asked for a sign-in, or the application root.</li>
 *   <li>Turned down: the sign-in page, with {@code error} as for a wrong
 *       password and the reason in {@link IdentityPaths#EXTERNAL_ERROR_PARAMETER},
 *       so that the page can say what to do. None of the reasons names an
 *       account.</li>
 *   <li>A link, either way: back to the linked-accounts view, which says how
 *       it went.</li>
 * </ul>
 */
final class ExternalSignInHandlers {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalSignInHandlers.class);

    private final SavedRequestAwareAuthenticationSuccessHandler signedIn =
            new SavedRequestAwareAuthenticationSuccessHandler();
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    /** The request cache the form login uses; known only once the chain is configured. */
    void useRequestCache(RequestCache requestCache) {
        signedIn.setRequestCache(requestCache);
    }

    AuthenticationSuccessHandler success() {
        return (request, response, authentication) -> {
            if (wasLinking(request)) {
                String registrationId = authentication instanceof OAuth2AuthenticationToken token
                        ? token.getAuthorizedClientRegistrationId()
                        : "";
                redirectStrategy.sendRedirect(request, response,
                        "/" + IdentityPaths.LINKED_ACCOUNTS + "?" + IdentityPaths.LINKED_PARAMETER + "=" + registrationId);
                return;
            }
            signedIn.onAuthenticationSuccess(request, response, authentication);
        };
    }

    AuthenticationFailureHandler failure() {
        return (request, response, exception) -> {
            Reason reason = reasonOf(exception);
            if (reason == Reason.FAILED) {
                // Most often a registration that does not match the provider
                // (secret, redirect URI, scopes); nobody finds that from the
                // sign-in page.
                LOG.warn("Sign-in through an external provider failed", exception);
            } else {
                LOG.debug("Sign-in through an external provider turned down: {}", exception.getMessage());
            }
            String target = wasLinking(request)
                    ? "/" + IdentityPaths.LINKED_ACCOUNTS + "?" + IdentityPaths.EXTERNAL_ERROR_PARAMETER + "="
                    + reason.code()
                    : "/" + IdentityPaths.LOGIN + "?error&" + IdentityPaths.EXTERNAL_ERROR_PARAMETER + "="
                    + reason.code();
            redirectStrategy.sendRedirect(request, response, target);
        };
    }

    static Reason reasonOf(AuthenticationException exception) {
        if (exception instanceof ExternalSignInException refused) {
            return refused.getReason();
        }
        if (exception instanceof DisabledException) {
            return Reason.DISABLED;
        }
        return Reason.FAILED;
    }

    /** Whether the round trip was a link; the note goes either way. */
    private static boolean wasLinking(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(ExternalAuthorizationRequestResolver.LINKING_SESSION_ATTRIBUTE) == null) {
            return false;
        }
        session.removeAttribute(ExternalAuthorizationRequestResolver.LINKING_SESSION_ATTRIBUTE);
        return true;
    }
}
