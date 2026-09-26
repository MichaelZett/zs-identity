package de.zettsystems.identity.configuration;

import de.zettsystems.identity.values.ExternalIdentityClaims;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;

/**
 * Reads what a provider said about the person signing in into the shape the
 * building block decides on (since 1.2.0).
 *
 * <p>The standard reader ({@link #standard()}) knows OpenID Connect -- Google,
 * Microsoft Entra ID, Keycloak and every other provider with an ID token --,
 * plain OAuth2 providers that report {@code email} and {@code email_verified}
 * in their user info, and GitHub, which reports neither reliably and is asked
 * for the verified primary address separately (scope {@code user:email}).
 *
 * <p>An application with a provider that answers differently declares a bean
 * of this type; {@link IdentityOAuth2Configurer} takes it instead. Whatever it
 * returns decides who gets in: an address must only count as verified when
 * the provider really vouches for it.
 */
@FunctionalInterface
public interface ExternalClaimsReader {

    /**
     * Reads the claims.
     *
     * @param authentication the provider's answer, after Spring has checked it
     *                       (token exchange, ID token signature, user info)
     */
    ExternalIdentityClaims read(OAuth2LoginAuthenticationToken authentication);

    /** The reader the building block uses unless the application brings its own. */
    static ExternalClaimsReader standard() {
        return new StandardClaimsReader();
    }
}
