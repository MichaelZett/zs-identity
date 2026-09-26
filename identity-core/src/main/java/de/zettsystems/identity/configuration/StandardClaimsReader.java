package de.zettsystems.identity.configuration;

import de.zettsystems.identity.values.ExternalIdentityClaims;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The reader {@link ExternalClaimsReader#standard()} hands out.
 *
 * <p>Three kinds of answer:
 *
 * <ul>
 *   <li><strong>OpenID Connect</strong>: the standard claims of the ID token
 *       and user info -- {@code sub}, {@code email}, {@code email_verified},
 *       the names, {@code locale}. An address counts only with
 *       {@code email_verified = true}; a provider that leaves the claim out
 *       does not vouch for anything.</li>
 *   <li><strong>GitHub</strong> (registration id {@code github}, or user info
 *       at {@code api.github.com}): the user info names the person by a
 *       numeric id and carries an address the person chose to make public,
 *       verified or not. The address that counts is the primary one from
 *       {@code /user/emails}, and only if GitHub marks it verified; that
 *       needs the scope {@code user:email}. Without it there is no address,
 *       and only an identity already linked gets in.</li>
 *   <li><strong>Any other OAuth2 provider</strong>: the user info attributes
 *       under the OIDC names, the subject from the registration's
 *       {@code user-name-attribute}.</li>
 * </ul>
 */
final class StandardClaimsReader implements ExternalClaimsReader {

    private static final Logger LOG = LoggerFactory.getLogger(StandardClaimsReader.class);

    private static final String GITHUB = "github";
    private static final String GITHUB_API_HOST = "api.github.com";
    private static final ParameterizedTypeReference<List<Map<String, Object>>> EMAIL_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    StandardClaimsReader() {
        this(RestClient.create());
    }

    StandardClaimsReader(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ExternalIdentityClaims read(OAuth2LoginAuthenticationToken authentication) {
        ClientRegistration registration = authentication.getClientRegistration();
        String registrationId = registration.getRegistrationId();
        OAuth2User user = ExternalSignInConverter.providerUser(authentication);
        if (user instanceof OidcUser oidc) {
            return new ExternalIdentityClaims(registrationId,
                    Objects.requireNonNull(oidc.getSubject(), "the ID token's sub"), oidc.getEmail(),
                    Boolean.TRUE.equals(oidc.getEmailVerified()), oidc.getGivenName(), oidc.getFamilyName(),
                    oidc.getFullName(), locale(oidc.getLocale()));
        }
        Map<String, Object> attributes = user.getAttributes();
        if (isGitHub(registration)) {
            String email = gitHubPrimaryVerifiedEmail(registration,
                    Objects.requireNonNull(authentication.getAccessToken(), "the access token").getTokenValue());
            String name = text(attributes, "name");
            return new ExternalIdentityClaims(registrationId, user.getName(), email, email != null, null, null,
                    name != null ? name : text(attributes, "login"), null);
        }
        return new ExternalIdentityClaims(registrationId, user.getName(), text(attributes, "email"),
                isTrue(attributes.get("email_verified")), text(attributes, "given_name"),
                text(attributes, "family_name"), text(attributes, "name"), locale(text(attributes, "locale")));
    }

    static boolean isGitHub(ClientRegistration registration) {
        if (GITHUB.equals(registration.getRegistrationId())) {
            return true;
        }
        String userInfoUri = registration.getProviderDetails().getUserInfoEndpoint().getUri();
        return userInfoUri != null && GITHUB_API_HOST.equalsIgnoreCase(URI.create(userInfoUri).getHost());
    }

    /**
     * The primary address, if GitHub marks it verified. {@code /user/emails}
     * sits next to {@code /user}, on github.com as on an Enterprise server.
     * A failed call leaves the person without an address rather than without
     * a sign-in: an identity linked before still gets in.
     */
    private @Nullable String gitHubPrimaryVerifiedEmail(ClientRegistration registration, String accessToken) {
        String userInfoUri = registration.getProviderDetails().getUserInfoEndpoint().getUri();
        if (userInfoUri == null || userInfoUri.isBlank()) {
            return null;
        }
        String emailsUri = (userInfoUri.endsWith("/") ? userInfoUri : userInfoUri + "/") + "emails";
        try {
            List<Map<String, Object>> emails = restClient.get()
                    .uri(emailsUri)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(EMAIL_LIST);
            if (emails == null) {
                return null;
            }
            return emails.stream()
                    .filter(entry -> isTrue(entry.get("primary")) && isTrue(entry.get("verified")))
                    .map(entry -> text(entry, "email"))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (RestClientException e) {
            LOG.warn("Could not read the addresses of a GitHub account from {} -- is the scope user:email "
                    + "requested?", emailsUri, e);
            return null;
        }
    }

    private static @Nullable String text(Map<String, Object> attributes, String name) {
        Object value = attributes.get(name);
        return value == null ? null : value.toString();
    }

    /** Some providers send {@code "true"} as a string. */
    private static boolean isTrue(@Nullable Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    /** {@code de}, {@code en-US}, and the {@code de_DE} some providers write. */
    static @Nullable Locale locale(@Nullable String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        Locale locale = Locale.forLanguageTag(tag.strip().replace('_', '-'));
        return locale.getLanguage().isEmpty() ? null : locale;
    }
}
