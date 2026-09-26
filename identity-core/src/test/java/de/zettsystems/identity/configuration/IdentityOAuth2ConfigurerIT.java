package de.zettsystems.identity.configuration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.zettsystems.identity.application.ExternalSignInService;
import de.zettsystems.identity.application.ExternalSignInUser;
import de.zettsystems.identity.application.IdentityMailSender;
import de.zettsystems.identity.application.IdentityUserDetails;
import de.zettsystems.identity.application.InvitationService;
import de.zettsystems.identity.application.RoleCatalog;
import de.zettsystems.identity.application.UserAccountService;
import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.PostgresTestImage;
import de.zettsystems.identity.testsupport.RecordingMailSender;
import de.zettsystems.identity.values.IdentityPaths;
import de.zettsystems.identity.values.RoleDefinition;
import de.zettsystems.identity.values.UserAccountDto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole round trip through Spring Security's OAuth2 sign-in, against a
 * provider played by a small HTTP server: redirect, callback, token exchange,
 * user info, and what the building block makes of it.
 *
 * <p>A plain OAuth2 provider rather than OpenID Connect: an ID token would
 * need keys and signatures, and the part that differs -- which claims are
 * read -- is covered by {@code StandardClaimsReaderTest}. Everything else,
 * the wiring above all, is the same.
 */
@SpringBootTest(classes = IdentityOAuth2ConfigurerIT.OAuth2Application.class, properties = {
        "zs.identity.oauth2.enabled=true",
        "spring.security.oauth2.client.registration.test.client-id=client",
        "spring.security.oauth2.client.registration.test.client-secret=secret",
        "spring.security.oauth2.client.registration.test.client-name=Test Provider",
        "spring.security.oauth2.client.registration.test.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.test.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.registration.test.scope=read",
        "spring.security.oauth2.client.provider.test.user-name-attribute=id"})
class IdentityOAuth2ConfigurerIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresTestImage.resolve())
            .withDatabaseName("identity")
            .withUsername("app")
            .withPassword("app")
            .withReuse(true);

    /** What the provider's user info answers next; the tests set it. */
    private static final AtomicReference<String> USER_INFO = new AtomicReference<>("{}");
    private static final HttpServer PROVIDER;

    static {
        POSTGRES.start();
        try {
            PROVIDER = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        PROVIDER.createContext("/token", exchange -> json(exchange,
                "{\"access_token\":\"access\",\"token_type\":\"Bearer\",\"expires_in\":3600}"));
        PROVIDER.createContext("/user", exchange -> json(exchange, USER_INFO.get()));
        PROVIDER.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        String provider = "http://localhost:" + PROVIDER.getAddress().getPort();
        registry.add("spring.security.oauth2.client.provider.test.authorization-uri", () -> provider + "/authorize");
        registry.add("spring.security.oauth2.client.provider.test.token-uri", () -> provider + "/token");
        registry.add("spring.security.oauth2.client.provider.test.user-info-uri", () -> provider + "/user");
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.stop(0);
    }

    /** An application's chain: its own rules, the providers, a form login, {@code anyRequest()} last. */
    @SpringBootApplication
    static class OAuth2Application {

        @Bean
        RoleCatalog applicationRoles() {
            return () -> Set.of(RoleDefinition.of("MEMBER", "role.member"));
        }

        @Bean
        IdentityMailSender testMailSender() {
            return new RecordingMailSender();
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) {
            http.authorizeHttpRequests(requests -> requests.requestMatchers("/public/**").permitAll())
                    .with(IdentityOAuth2Configurer.oauth2Login(), Customizer.withDefaults())
                    .formLogin(login -> login.loginPage("/login").permitAll())
                    .authorizeHttpRequests(requests -> requests.anyRequest().authenticated());
            return http.build();
        }
    }

    @Autowired
    private WebApplicationContext webContext;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private InvitationService invitationService;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private AuthTokenRepository tokenRepository;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private IdentityMailSender mailSender;
    @Autowired
    private FilterChainProxy filterChain;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        ((RecordingMailSender) mailSender).clear();
    }

    @Test
    void theFirstSignInCreatesAnAccountAndSignsItIn() throws Exception {
        USER_INFO.set("{\"id\":7,\"email\":\"ida@example.com\",\"email_verified\":true,\"name\":\"Ida Beispiel\"}");

        MockHttpSession session = new MockHttpSession();
        MockHttpServletResponse callback = roundTrip(session, "");

        assertThat(callback.getRedirectedUrl()).isEqualTo("/");
        Authentication signedIn = authenticationIn(session);
        assertThat(signedIn).isInstanceOf(OAuth2AuthenticationToken.class);
        assertThat(((OAuth2AuthenticationToken) signedIn).getAuthorizedClientRegistrationId()).isEqualTo("test");
        assertThat(signedIn.getName()).isEqualTo("ida@example.com");
        assertThat(signedIn.getPrincipal())
                .as("an IdentityUserDetails like after any other sign-in, and the OAuth2User Spring needs")
                .isInstanceOf(IdentityUserDetails.class)
                .isInstanceOf(ExternalSignInUser.class);
        assertThat(((ExternalSignInUser) signedIn.getPrincipal()).getAttributes()).containsEntry("id", 7);
        assertThat(signedIn.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_USER", FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY);
        UserAccountDto account = userAccountService.findByEmail("ida@example.com").orElseThrow();
        assertThat(account.hasPassword()).isFalse();
        assertThat(account.displayName()).isEqualTo("Ida Beispiel");
        assertThat(userRepository.findById(account.id()).orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    void aRefusalGoesBackToTheSignInPageWithTheReason() throws Exception {
        USER_INFO.set("{\"id\":7,\"email\":\"ida@example.com\",\"email_verified\":false}");

        MockHttpSession session = new MockHttpSession();
        MockHttpServletResponse callback = roundTrip(session, "");

        assertThat(callback.getRedirectedUrl())
                .isEqualTo("/login?error&" + IdentityPaths.EXTERNAL_ERROR_PARAMETER + "=email-unverified");
        assertThat(authenticationIn(session)).isNull();
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void aDisabledAccountIsTurnedDownWithItsReason() throws Exception {
        UserAccountDto account = userAccountService.createAccount("ida@example.com", "ein-langes-passwort",
                "Ida", "Beispiel", true);
        userAccountService.setEnabled(account.id(), false);
        USER_INFO.set("{\"id\":7,\"email\":\"ida@example.com\",\"email_verified\":true}");

        MockHttpServletResponse callback = roundTrip(new MockHttpSession(), "");

        assertThat(callback.getRedirectedUrl()).endsWith("external=disabled");
    }

    @Test
    void anInvitationTravelsThroughTheRoundTrip() throws Exception {
        UserAccountDto invited = invitationService.inviteNewAccount("ida@club.example", "Ida", "Beispiel");
        String token = ((RecordingMailSender) mailSender).tokenFromLastMailTo("ida@club.example");
        USER_INFO.set("{\"id\":7,\"email\":\"ida.private@example.org\",\"email_verified\":false}");

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ExternalSignInService.PENDING_INVITATION_SESSION_ATTRIBUTE, token);
        roundTrip(session, "?" + IdentityPaths.INVITATION_PARAMETER);

        assertThat(authenticationIn(session).getName()).isEqualTo("ida@club.example");
        assertThat(userAccountService.findById(invited.id()).orElseThrow().claimed()).isTrue();
        assertThat(session.getAttribute(ExternalSignInService.PENDING_INVITATION_SESSION_ATTRIBUTE))
                .as("used for one round trip only")
                .isNull();
    }

    /**
     * A link carrying somebody else's invitation must not tie the identity of
     * whoever follows it to that account: the token counts only from the
     * session, where the redemption view puts it.
     */
    @Test
    void anInvitationInTheAddressIsIgnored() throws Exception {
        UserAccountDto invited = invitationService.inviteNewAccount("ida@club.example", "Ida", "Beispiel");
        String token = ((RecordingMailSender) mailSender).tokenFromLastMailTo("ida@club.example");
        USER_INFO.set("{\"id\":666,\"email\":\"victim@example.org\",\"email_verified\":false}");

        MockHttpServletResponse callback = roundTrip(new MockHttpSession(),
                "?" + IdentityPaths.INVITATION_PARAMETER + "=" + token);

        assertThat(callback.getRedirectedUrl()).endsWith("external=email-unverified");
        assertThat(userAccountService.findById(invited.id()).orElseThrow().claimed()).isFalse();
    }

    @Test
    void aFreshlySignedInAccountLinksTheProviderAndComesBackToItsList() throws Exception {
        UserAccountDto account = userAccountService.createAccount("ida@example.com", "ein-langes-passwort",
                "Ida", "Beispiel", true);
        USER_INFO.set("{\"id\":42,\"email\":\"somebody.else@example.org\",\"email_verified\":true}");
        MockHttpSession session = sessionOf(passwordSignIn("ida@example.com"));

        MockHttpServletResponse callback = roundTrip(session, "?" + IdentityPaths.LINK_PARAMETER);

        assertThat(callback.getRedirectedUrl())
                .isEqualTo("/" + IdentityPaths.LINKED_ACCOUNTS + "?" + IdentityPaths.LINKED_PARAMETER + "=test");
        assertThat(((IdentityUserDetails) authenticationIn(session).getPrincipal()).userId()).isEqualTo(account.id());
        assertThat(userAccountService.findByEmail("somebody.else@example.org"))
                .as("the provider's address plays no part in a link")
                .isEmpty();
    }

    /** A remembered session on a shared computer must not add a way into the account. */
    @Test
    void aRememberedSessionCannotLinkAProvider() throws Exception {
        userAccountService.createAccount("ida@example.com", "ein-langes-passwort", "Ida", "Beispiel", true);
        USER_INFO.set("{\"id\":42,\"email\":\"ida@example.com\",\"email_verified\":true}");
        IdentityUserDetails user = (IdentityUserDetails) userDetailsService.loadUserByUsername("ida@example.com");
        MockHttpSession session = sessionOf(new RememberMeAuthenticationToken("key", user, user.getAuthorities()));

        MockHttpServletResponse callback = roundTrip(session, "?" + IdentityPaths.LINK_PARAMETER);

        assertThat(callback.getRedirectedUrl())
                .isEqualTo("/" + IdentityPaths.LINKED_ACCOUNTS + "?" + IdentityPaths.EXTERNAL_ERROR_PARAMETER
                        + "=reauthentication-required");
    }

    /** With a login page of our own Spring neither generates one nor sends everybody straight to the provider. */
    @Test
    void anUnauthenticatedRequestIsSentToTheSignInPage() throws Exception {
        MockHttpServletResponse response = call(get("/somewhere", new MockHttpSession()));

        assertThat(response.getRedirectedUrl()).endsWith("/login");
    }

    /** Redirect to the provider, then the callback with the state the redirect carried. */
    private MockHttpServletResponse roundTrip(MockHttpSession session, String query) throws Exception {
        MockHttpServletRequest start = get(IdentityPaths.OAUTH2_AUTHORIZATION + "/test", session);
        UriComponentsBuilder.fromUriString(query).build().getQueryParams()
                .forEach((name, values) -> start.addParameter(name, values.getFirst() == null ? "" : values.getFirst()));
        String location = call(start).getRedirectedUrl();
        assertThat(location).startsWith("http://localhost:" + PROVIDER.getAddress().getPort() + "/authorize");
        String state = URLDecoder.decode(
                UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state"),
                StandardCharsets.UTF_8);

        MockHttpServletRequest callback = get("/login/oauth2/code/test", session);
        callback.addParameter("code", "the-code");
        callback.addParameter("state", state);
        return call(callback);
    }

    private MockHttpServletRequest get(String path, MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest(webContext.getServletContext(), "GET", path);
        request.setServerName("localhost");
        request.setSession(session);
        return request;
    }

    private MockHttpServletResponse call(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            filterChain.doFilter(request, response, new MockFilterChain());
        } finally {
            SecurityContextHolder.clearContext();
        }
        return response;
    }

    private Authentication passwordSignIn(String email) {
        IdentityUserDetails user = (IdentityUserDetails) userDetailsService.loadUserByUsername(email);
        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }

    private static MockHttpSession sessionOf(Authentication authentication) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication));
        return session;
    }

    private static Authentication authenticationIn(MockHttpSession session) {
        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        return context == null ? null : context.getAuthentication();
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
