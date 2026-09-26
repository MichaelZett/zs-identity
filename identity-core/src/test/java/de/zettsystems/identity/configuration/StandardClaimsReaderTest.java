package de.zettsystems.identity.configuration;

import com.sun.net.httpserver.HttpServer;
import de.zettsystems.identity.values.ExternalIdentityClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class StandardClaimsReaderTest {

    private HttpServer gitHub;

    @AfterEach
    void stop() {
        if (gitHub != null) {
            gitHub.stop(0);
        }
    }

    @Test
    void openIdConnectClaimsAreTakenAsTheyAre() {
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token")
                .subject("sub-1")
                .claim("email", "ida@example.com")
                .claim("email_verified", true)
                .claim("given_name", "Ida")
                .claim("family_name", "Beispiel")
                .claim("name", "Ida Beispiel")
                .claim("locale", "de_DE")
                .issuedAt(Instant.parse("2026-09-01T10:00:00Z"))
                .expiresAt(Instant.parse("2026-09-01T11:00:00Z"))
                .build();

        ExternalIdentityClaims claims = ExternalClaimsReader.standard()
                .read(token(registration("google", "http://localhost/userinfo"), new DefaultOidcUser(List.of(), idToken)));

        assertThat(claims).isEqualTo(new ExternalIdentityClaims("google", "sub-1", "ida@example.com", true, "Ida",
                "Beispiel", "Ida Beispiel", Locale.GERMANY));
    }

    /** A provider that leaves {@code email_verified} out does not vouch for anything. */
    @Test
    void anOpenIdConnectAddressWithoutTheFlagIsNotVerified() {
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token")
                .subject("sub-1")
                .claim("email", "ida@example.com")
                .issuedAt(Instant.parse("2026-09-01T10:00:00Z"))
                .expiresAt(Instant.parse("2026-09-01T11:00:00Z"))
                .build();

        ExternalIdentityClaims claims = ExternalClaimsReader.standard()
                .read(token(registration("keycloak", "http://localhost/userinfo"),
                        new DefaultOidcUser(List.of(), idToken)));

        assertThat(claims.verifiedEmail()).isNull();
    }

    @Test
    void aPlainOAuth2ProviderIsReadUnderTheOpenIdNames() {
        OAuth2User user = new DefaultOAuth2User(List.of(),
                Map.of("uid", "u-9", "email", "ida@example.com", "email_verified", "true", "name", "Ida"), "uid");

        ExternalIdentityClaims claims = ExternalClaimsReader.standard()
                .read(token(registration("corporate", "http://localhost/userinfo"), user));

        assertThat(claims.subject()).isEqualTo("u-9");
        assertThat(claims.verifiedEmail()).as("some providers send the flag as a string").isEqualTo("ida@example.com");
        assertThat(claims.fullName()).isEqualTo("Ida");
    }

    /**
     * GitHub's user info carries whatever public address the person chose,
     * verified or not. What counts is the verified primary one from
     * {@code /user/emails}, asked with the access token.
     */
    @Test
    void gitHubIsAskedForTheVerifiedPrimaryAddress() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        String userInfoUri = startGitHub(authorization, """
                [{"email":"old@example.com","primary":false,"verified":true},
                 {"email":"ida@example.com","primary":true,"verified":true}]
                """);
        OAuth2User user = new DefaultOAuth2User(List.of(),
                Map.of("id", 42, "login", "ida", "email", "public@example.com"), "id");

        ExternalIdentityClaims claims = ExternalClaimsReader.standard().read(token(registration("github", userInfoUri), user));

        assertThat(claims.subject()).isEqualTo("42");
        assertThat(claims.email()).isEqualTo("ida@example.com");
        assertThat(claims.emailVerified()).isTrue();
        assertThat(claims.fullName()).as("no name: the login instead").isEqualTo("ida");
        assertThat(authorization.get()).isEqualTo("Bearer access");
    }

    @Test
    void anUnverifiedPrimaryAddressAtGitHubCountsForNothing() throws IOException {
        String userInfoUri = startGitHub(new AtomicReference<>(), """
                [{"email":"ida@example.com","primary":true,"verified":false}]
                """);
        OAuth2User user = new DefaultOAuth2User(List.of(), Map.of("id", 42, "login", "ida"), "id");

        ExternalIdentityClaims claims = ExternalClaimsReader.standard().read(token(registration("github", userInfoUri), user));

        assertThat(claims.email()).isNull();
        assertThat(claims.emailVerified()).isFalse();
    }

    /** Without the scope user:email GitHub refuses; the person keeps a sign-in, just no address. */
    @Test
    void aRefusedAddressListLeavesThePersonWithoutAddress() {
        OAuth2User user = new DefaultOAuth2User(List.of(), Map.of("id", 42, "login", "ida"), "id");

        ExternalIdentityClaims claims = ExternalClaimsReader.standard()
                .read(token(registration("github", "http://localhost:1/user"), user));

        assertThat(claims.subject()).isEqualTo("42");
        assertThat(claims.verifiedEmail()).isNull();
    }

    @Test
    void gitHubIsRecognisedByItsApiHostToo() {
        assertThat(StandardClaimsReader.isGitHub(registration("octo", "https://api.github.com/user"))).isTrue();
        assertThat(StandardClaimsReader.isGitHub(registration("octo", "https://example.com/user"))).isFalse();
    }

    @Test
    void localesAreReadLeniently() {
        assertThat(StandardClaimsReader.locale("en-US")).isEqualTo(Locale.US);
        assertThat(StandardClaimsReader.locale("de_DE")).isEqualTo(Locale.GERMANY);
        assertThat(StandardClaimsReader.locale(" ")).isNull();
        assertThat(StandardClaimsReader.locale("123")).isNull();
    }

    private String startGitHub(AtomicReference<String> authorization, String emails) throws IOException {
        gitHub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        gitHub.createContext("/user/emails", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = emails.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        gitHub.start();
        return "http://localhost:" + gitHub.getAddress().getPort() + "/user";
    }

    private static ClientRegistration registration(String registrationId, String userInfoUri) {
        return ClientRegistration.withRegistrationId(registrationId)
                .clientId("client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("http://localhost/authorize")
                .tokenUri("http://localhost/token")
                .userInfoUri(userInfoUri)
                .userNameAttributeName("id")
                .build();
    }

    private static OAuth2LoginAuthenticationToken token(ClientRegistration registration, OAuth2User user) {
        OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("http://localhost/authorize")
                .clientId("client")
                .redirectUri("http://localhost/login/oauth2/code/" + registration.getRegistrationId())
                .state("state")
                .build();
        OAuth2AuthorizationResponse response = OAuth2AuthorizationResponse.success("code")
                .redirectUri("http://localhost/login/oauth2/code/" + registration.getRegistrationId())
                .state("state")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access",
                Instant.now(), Instant.now().plusSeconds(60));
        return new OAuth2LoginAuthenticationToken(registration, new OAuth2AuthorizationExchange(request, response),
                user, List.of(), accessToken);
    }
}
