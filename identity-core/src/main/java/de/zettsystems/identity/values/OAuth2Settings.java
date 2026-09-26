package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Sign-in through external identity providers (OAuth2/OIDC), prefix
 * {@code zs.identity.oauth2}, since 1.2.0.
 *
 * <p>Only what the building block decides lives here. The providers
 * themselves -- client id, secret, issuer, scopes -- are Spring Boot's
 * {@code spring.security.oauth2.client.registration.*} and
 * {@code spring.security.oauth2.client.provider.*}, so that secrets have one
 * place and nobody learns a second format.
 *
 * <p>Off by default, like passkeys: the line in the application's filter
 * chain stays the same across environments, the property decides.
 *
 * @param enabled        whether the sign-in through providers is offered at all
 * @param registrations  which client registrations the sign-in page offers,
 *                       by registration id and in this order; empty means
 *                       every registration the application has configured,
 *                       ordered by name
 * @param createAccounts whether the first sign-in of an unknown person creates
 *                       an account. {@code null} (the default) follows
 *                       {@code zs.identity.self-registration-enabled}: an
 *                       application that does not let people register
 *                       themselves does not let them in through a provider
 *                       either. Invitations work regardless.
 * @param linkByEmail    whether the first sign-in through a provider may
 *                       join an existing account with the same address. Only
 *                       when the provider vouches for the address; off means
 *                       the person links the provider from within the
 *                       account instead.
 */
public record OAuth2Settings(@DefaultValue("false") boolean enabled,
                             @DefaultValue List<String> registrations,
                             @Nullable Boolean createAccounts,
                             @DefaultValue("true") boolean linkByEmail) {

    public OAuth2Settings {
        registrations = registrations == null ? List.of() : List.copyOf(registrations);
        for (String registration : registrations) {
            if (registration.isBlank()) {
                throw new IllegalArgumentException("zs.identity.oauth2.registrations must not contain blank entries");
            }
        }
    }

    /** Defaults for tests that build the record by hand: switched off. */
    public static OAuth2Settings defaults() {
        return new OAuth2Settings(false, List.of(), null, true);
    }

    /** The same settings switched on or off; see {@code IdentityProperties#withOAuth2}. */
    public OAuth2Settings enabled(boolean newEnabled) {
        return new OAuth2Settings(newEnabled, registrations, createAccounts, linkByEmail);
    }

    /** The same settings with a decision of their own about new accounts. */
    public OAuth2Settings createAccounts(@Nullable Boolean newCreateAccounts) {
        return new OAuth2Settings(enabled, registrations, newCreateAccounts, linkByEmail);
    }

    /** The same settings with or without joining existing accounts by address. */
    public OAuth2Settings linkByEmail(boolean newLinkByEmail) {
        return new OAuth2Settings(enabled, registrations, createAccounts, newLinkByEmail);
    }

    /** The same settings offering these registrations, in this order. */
    public OAuth2Settings registrations(List<String> newRegistrations) {
        return new OAuth2Settings(enabled, newRegistrations, createAccounts, linkByEmail);
    }
}
