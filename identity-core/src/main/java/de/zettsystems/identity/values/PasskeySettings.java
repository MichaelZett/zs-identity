package de.zettsystems.identity.values;

import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Sign-in with passkeys (WebAuthn), prefix {@code zs.identity.passkeys}.
 *
 * <p>Off by default: a passkey is bound to the domain it was created on, so
 * an application switches it on per environment once its domain is settled.
 * With {@code enabled=false} the security configurer of the building block
 * registers nothing, the sign-in page shows no passkey button and the
 * management view says so -- the code of an application stays the same
 * across environments, only the values differ.
 *
 * @param enabled        whether passkeys can be registered and used
 * @param rpId           the relying party id: the domain the passkeys are
 *                       bound to, without scheme or port
 *                       ({@code orgaapp.example.com}). {@code localhost}
 *                       works for local development.
 * @param rpName         the name the authenticator shows for this
 *                       application when a passkey is created
 * @param allowedOrigins the origins the browser may sign in from, with
 *                       scheme and port ({@code https://orgaapp.example.com},
 *                       locally {@code http://localhost:8090}). An origin
 *                       must belong to the {@code rpId} or one of its
 *                       subdomains, or the browser refuses.
 * @param loginButton    whether the sign-in page shows the button "Sign in
 *                       with passkey". On by default. An application that
 *                       finds the offer in the username field enough can
 *                       switch it off and keep the page quiet -- but it is
 *                       the only way in where the browser has no conditional
 *                       mediation, or where its list does not turn the
 *                       passkey up, so switching it off is a decision about
 *                       the people it leaves behind, not a matter of taste.
 *                       Deliberately a setting and not something decided in
 *                       the browser: whether conditional mediation works
 *                       says nothing about whether this person has a passkey
 *                       here, the offer only appears once the field is
 *                       touched, and a button that comes and goes is one
 *                       nobody can be told to press.
 */
public record PasskeySettings(@DefaultValue("false") boolean enabled,
                              @DefaultValue("localhost") String rpId,
                              @DefaultValue("Application") String rpName,
                              @DefaultValue("http://localhost:8080") List<String> allowedOrigins,
                              @DefaultValue("true") boolean loginButton) {

    /**
     * {@code @ConstructorBinding} because the record has a second
     * constructor for the shape up to 0.13.x: with more than one, Spring
     * cannot tell which one binds the properties, and the whole
     * {@code zs.identity} tree fails to bind.
     */
    @ConstructorBinding
    public PasskeySettings {
        if (rpId.isBlank()) {
            throw new IllegalArgumentException("zs.identity.passkeys.rp-id must not be blank");
        }
        if (rpName.isBlank()) {
            throw new IllegalArgumentException("zs.identity.passkeys.rp-name must not be blank");
        }
        if (allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("zs.identity.passkeys.allowed-origins must name at least one origin");
        }
        allowedOrigins = allowedOrigins.stream().map(PasskeySettings::stripTrailingSlash).toList();
    }

    /**
     * The shape up to 0.13.x, when the button was the only way in and always
     * there. Kept so that an application building the record by hand goes on
     * compiling; see the same seam on {@code UserAccountDto}.
     */
    public PasskeySettings(boolean enabled, String rpId, String rpName, List<String> allowedOrigins) {
        this(enabled, rpId, rpName, allowedOrigins, true);
    }

    /** Defaults for tests that build the record by hand: switched off. */
    public static PasskeySettings defaults() {
        return new PasskeySettings(false, "localhost", "Application", List.of("http://localhost:8080"), true);
    }

    /** The same settings switched on; see {@code IdentityProperties#withPasskeys}. */
    public PasskeySettings enabled(boolean newEnabled) {
        return new PasskeySettings(newEnabled, rpId, rpName, allowedOrigins, loginButton);
    }

    /** The same settings with or without the button on the sign-in page. */
    public PasskeySettings loginButton(boolean newLoginButton) {
        return new PasskeySettings(enabled, rpId, rpName, allowedOrigins, newLoginButton);
    }

    /** An origin is compared byte for byte by the browser; a trailing slash would never match. */
    private static String stripTrailingSlash(String origin) {
        return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
    }
}
