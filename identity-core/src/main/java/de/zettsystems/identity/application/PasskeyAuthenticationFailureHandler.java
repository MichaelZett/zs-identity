package de.zettsystems.identity.application;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Answers a failed passkey sign-in with {@code 401} and a small JSON body
 * that names the reason in one word.
 *
 * <p>The sign-in with a passkey is a {@code fetch} from the page, not a form
 * submission, so a redirect to {@code /login?error} -- the answer of the
 * form login -- would be the wrong shape. The page reads {@code reason} and
 * shows the matching text: {@value #REASON_DISABLED} for an account that
 * may not sign in, {@value #REASON_FAILED} for everything else. Deliberately
 * no more detail than that: an unknown credential and a bad signature look
 * the same from the outside, as they do for a password.
 */
public final class PasskeyAuthenticationFailureHandler implements AuthenticationFailureHandler {

    /** The account exists but is disabled, locked or expired. */
    public static final String REASON_DISABLED = "disabled";
    /** Unknown passkey, bad signature, stale challenge: not this account. */
    public static final String REASON_FAILED = "failed";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // The reason is one of two fixed words, so no JSON library is needed
        // -- and none may be assumed, this class lives in the core.
        response.getWriter().write("{\"authenticated\":false,\"reason\":\"" + reasonOf(exception) + "\"}");
        response.getWriter().flush();
    }

    /** Package-visible for the test; the mapping is the whole point of the class. */
    static String reasonOf(AuthenticationException exception) {
        return exception instanceof AccountStatusException ? REASON_DISABLED : REASON_FAILED;
    }
}
