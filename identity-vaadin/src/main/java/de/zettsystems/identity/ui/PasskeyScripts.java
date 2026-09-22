package de.zettsystems.identity.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.page.PendingJavaScriptResult;
import com.vaadin.flow.server.VaadinRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.security.web.csrf.CsrfToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The two WebAuthn ceremonies of the browser -- sign in with a passkey,
 * register one -- as scripts the views run through {@code executeJs}.
 *
 * <p>Plain scripts rather than a {@code @JsModule}: a module would have to
 * go through the application's frontend build, and the building block has
 * none of its own. {@code executeJs} takes the source as it is, and the two
 * files are small. They talk to Spring Security's WebAuthn filters (the
 * paths in {@code IdentityPaths}) and need the CSRF token for that, which
 * the servlet request of the current Vaadin round trip carries as an
 * attribute -- the same place Vaadin's own login form takes it from.
 *
 * <p>Each script returns a promise. It resolves with {@value #OK} on
 * success and rejects with one of the {@link #ERROR_CODES}, which the views
 * map to a text; anything else is treated as {@value #FAILED}. The
 * conditional sign-in has a third outcome, {@value #QUIET}: it offers rather
 * than asks, so an offer nobody took up resolves instead of rejecting and
 * the view says nothing. A rejection
 * may carry a detail behind the code ({@code failed HTTP 400}) -- only
 * digits, never text that could hold a second code. The views log it and
 * show the text of the code alone: a request the filters turn down answers
 * with an empty body, and without the status nobody can tell an access rule
 * from a broken ceremony.
 */
final class PasskeyScripts {

    static final String OK = "ok";
    /** What the conditional sign-in resolves with when nobody took the offer up. */
    static final String QUIET = "quiet";
    static final String UNSUPPORTED = "unsupported";
    static final String CANCELLED = "cancelled";
    static final String DISABLED = "disabled";
    static final String FAILED = "failed";
    /** In the order they are looked for in an error message. */
    static final List<String> ERROR_CODES = List.of(UNSUPPORTED, CANCELLED, DISABLED, FAILED);

    /** The mode the sign-in script takes as {@code $3}. */
    private static final String ON_REQUEST = "";
    private static final String CONDITIONAL = "conditional";

    private static final String AUTHENTICATE = load("passkey-authenticate.js");
    private static final String REGISTER = load("passkey-register.js");

    /**
     * Ends a waiting conditional request. Three lines, and they belong next
     * to the call rather than in a file of their own: the counterpart is
     * {@code armAbort} in {@code passkey-authenticate.js}, which parks the
     * controller on the same element.
     */
    private static final String ABORT_CONDITIONAL = """
            const waiting = this.__zsPasskeyConditional;
            if (waiting) {
              this.__zsPasskeyConditional = null;
              waiting.abort();
            }
            """;

    private PasskeyScripts() {
        // Utility class
    }

    /** Runs the sign-in ceremony because someone asked for it; the page navigates away on success. */
    static PendingJavaScriptResult authenticate(Component host) {
        Csrf csrf = Csrf.current();
        return host.getElement().executeJs(AUTHENTICATE, contextPath(), csrf.header, csrf.token, ON_REQUEST);
    }

    /**
     * Lets the browser offer the passkey in the sign-in form's username
     * field, instead of waiting for a button.
     *
     * <p>Runs <em>on the form's element</em>, not on the view: through
     * {@code this} the script reaches the username field it has to mark as
     * taking passkeys, and parks its {@code AbortController} there so that
     * {@link #abortConditional} finds it again. Same ceremony, same
     * endpoints -- only the browser's part is different, and nothing on the
     * server changes.
     *
     * <p>It resolves with {@value #QUIET} for every outcome in which nobody
     * took the offer up, including a browser that cannot do it at all. Only
     * a passkey the person really chose can still reject.
     */
    static PendingJavaScriptResult authenticateConditionally(Component loginForm) {
        Csrf csrf = Csrf.current();
        return loginForm.getElement().executeJs(AUTHENTICATE, contextPath(), csrf.header, csrf.token, CONDITIONAL);
    }

    /**
     * Ends a conditional request that is still waiting, on the element
     * {@link #authenticateConditionally} was started on.
     *
     * <p>Not tidiness: a browser answers a second
     * {@code navigator.credentials.get} while one is pending with
     * {@code NotAllowedError}. Without this, the button would fail for
     * exactly the people the offer did not reach.
     */
    static void abortConditional(Component loginForm) {
        loginForm.getElement().executeJs(ABORT_CONDITIONAL);
    }

    /** Runs the registration ceremony for a passkey with this label. */
    static PendingJavaScriptResult register(Component host, String label) {
        Csrf csrf = Csrf.current();
        return host.getElement().executeJs(REGISTER, contextPath(), csrf.header, csrf.token, label);
    }

    /**
     * The code inside an error message. Vaadin wraps what a script rejects
     * with, and the script may append a status, so the message is searched
     * rather than compared.
     */
    static String errorCode(@Nullable String message) {
        if (message == null) {
            return FAILED;
        }
        return ERROR_CODES.stream().filter(message::contains).findFirst().orElse(FAILED);
    }

    private static String contextPath() {
        VaadinRequest request = VaadinRequest.getCurrent();
        String contextPath = request != null ? request.getContextPath() : null;
        return contextPath != null ? contextPath : "";
    }

    private static String load(String name) {
        try (InputStream in = PasskeyScripts.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Missing script " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read script " + name, e);
        }
    }

    /** Header name and value of Spring's CSRF token; both empty when there is none. */
    private record Csrf(String header, String token) {

        static Csrf current() {
            VaadinRequest request = VaadinRequest.getCurrent();
            Object attribute = request != null ? request.getAttribute(CsrfToken.class.getName()) : null;
            if (attribute instanceof CsrfToken csrfToken) {
                return new Csrf(csrfToken.getHeaderName(), csrfToken.getToken());
            }
            return new Csrf("", "");
        }
    }
}
