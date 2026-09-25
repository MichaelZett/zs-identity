package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.IdentityMailSender;
import de.zettsystems.identity.application.IdentityUserDetails;
import de.zettsystems.identity.application.PasskeyAuthenticationProvider;
import de.zettsystems.identity.application.RoleCatalog;
import de.zettsystems.identity.application.UserAccountService;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.PostgresTestImage;
import de.zettsystems.identity.testsupport.RecordingMailSender;
import de.zettsystems.identity.values.IdentityPaths;
import de.zettsystems.identity.values.RoleDefinition;
import de.zettsystems.identity.values.UserAccountDto;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsFilter;
import org.springframework.security.web.webauthn.registration.WebAuthnRegistrationFilter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * The passkey endpoints in a real filter chain: who may call what, and the
 * pieces the sign-in filter has to carry so that a passkey sign-in behaves
 * like the form login (remember-me, the shared manager, JSON answers).
 *
 * <p>The WebAuthn ceremony itself -- an authenticator signing a challenge --
 * has no place in a test without a browser; that is checked on a device.
 * What can be checked here is everything around it, and that is where the
 * wiring mistakes would sit.
 */
@SpringBootTest(classes = IdentityPasskeyConfigurerIT.PasskeyApplication.class, properties = {
        "zs.identity.passkeys.enabled=true",
        "zs.identity.passkeys.rp-id=localhost",
        "zs.identity.passkeys.rp-name=Test",
        "zs.identity.passkeys.allowed-origins=http://localhost:8080"})
class IdentityPasskeyConfigurerIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresTestImage.resolve())
            .withDatabaseName("identity")
            .withUsername("app")
            .withPassword("app")
            .withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /**
     * An application's chain the way the README shows it: own rules first,
     * the passkeys, a form login with remember-me, and {@code anyRequest()}
     * last -- the place a Vaadin configurer would put it. CSRF stays on, as
     * in any real application: the sign-in scripts send the token, so every
     * request here carries one too (see {@link #post}).
     */
    @SpringBootApplication
    static class PasskeyApplication {

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
                    .with(IdentityPasskeyConfigurer.passkeys(), Customizer.withDefaults())
                    .formLogin(login -> login.loginPage("/login").permitAll())
                    .rememberMe(rememberMe -> rememberMe.key("test-key").alwaysRemember(true))
                    .authorizeHttpRequests(requests -> requests.anyRequest().authenticated());
            return http.build();
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private FilterChainProxy filterChain;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private PublicKeyCredentialUserEntityRepository userEntities;

    private UserAccountDto anna;

    @BeforeEach
    void anAccount() {
        userRepository.deleteAll();
        anna = userAccountService.createAccount("anna@example.com", "ein-langes-passwort", "Anna", "Beispiel", true);
    }

    @Test
    void anyoneMayAskForASignInChallenge() throws Exception {
        MockHttpServletResponse response = call(post(IdentityPaths.PASSKEY_AUTHENTICATION_OPTIONS), null);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body(response)).contains("\"challenge\"").contains("\"rpId\":\"localhost\"");
    }

    /** The answer of the sign-in is JSON, not the redirect of the form login. */
    @Test
    void aSignInThatCannotBeVerifiedIsAnsweredWithJson() throws Exception {
        MockHttpServletRequest request = post(IdentityPaths.PASSKEY_LOGIN);
        request.setContentType("application/json");
        request.setContent("{\"id\":\"nope\"}".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = call(request, null);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body(response)).isEqualTo("{\"authenticated\":false,\"reason\":\"failed\"}");
    }

    @Test
    void registeringNeedsASignIn() throws Exception {
        MockHttpServletResponse response = call(post(IdentityPaths.PASSKEY_REGISTRATION_OPTIONS), null);

        assertSentToSignIn(response);
    }

    /** The point of {@code fullyAuthenticated()}: the cookie alone is not enough to add a passkey. */
    @Test
    void aRememberMeSessionMayNotRegister() throws Exception {
        IdentityUserDetails user = (IdentityUserDetails) userDetailsService.loadUserByUsername(anna.email());
        Authentication rememberMe = new RememberMeAuthenticationToken("test-key", user, user.getAuthorities());

        MockHttpServletResponse response = call(post(IdentityPaths.PASSKEY_REGISTRATION_OPTIONS), rememberMe);

        assertSentToSignIn(response);
        assertThat(userEntities.findByUsername(anna.email())).as("nothing was set up").isNull();
    }

    @Test
    void aFreshSignInGetsARegistrationChallengeAndAUserHandle() throws Exception {
        IdentityUserDetails user = (IdentityUserDetails) userDetailsService.loadUserByUsername(anna.email());
        Authentication fresh = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());

        MockHttpServletResponse response = call(post(IdentityPaths.PASSKEY_REGISTRATION_OPTIONS), fresh);

        assertThat(response.getStatus()).isEqualTo(200);
        // Spring alone would put the address into displayName on the first
        // registration; the configurer swaps in the account's name.
        assertThat(body(response))
                .contains("\"name\":\"anna@example.com\"")
                .contains("\"displayName\":\"Anna Beispiel\"")
                .contains("\"rp\":{\"id\":\"localhost\",\"name\":\"Test\"}");
        assertThat(userEntities.findByUsername(anna.email())).as("the handle now lives on the account").isNotNull();
        assertThat(userRepository.findByEmail(anna.email()).orElseThrow().getPasskeyUserHandle()).isNotNull();
    }

    /**
     * What Spring's own configurer leaves out and this one has to carry: the
     * application's remember-me services, and the shared manager -- the one
     * that publishes the events the {@code LoginRecorder} listens for -- with
     * the building block's provider in it.
     */
    @Test
    void theSignInFilterCarriesRememberMeAndTheSharedManager() {
        WebAuthnAuthenticationFilter signIn = filterOf(WebAuthnAuthenticationFilter.class);

        Object rememberMe = ReflectionTestUtils.getField(signIn, "rememberMeServices");
        assertThat(rememberMe).as("the application's, not Spring's null implementation")
                .isInstanceOf(TokenBasedRememberMeServices.class);

        Object manager = ReflectionTestUtils.getField(signIn, "authenticationManager");
        assertThat(manager).isInstanceOf(ProviderManager.class);
        assertThat(((ProviderManager) manager).getProviders())
                .anyMatch(PasskeyAuthenticationProvider.class::isInstance);
    }

    /** The registration filters sit behind the authorization filter, so that the rules above apply. */
    @Test
    void theRegistrationFiltersComeAfterAuthorization() {
        List<Filter> filters = filterChain.getFilterChains().getFirst().getFilters();
        int authorization = indexOf(filters, "AuthorizationFilter");

        assertThat(indexOf(filters, WebAuthnRegistrationFilter.class.getSimpleName())).isGreaterThan(authorization);
        assertThat(indexOf(filters, PublicKeyCredentialCreationOptionsFilter.class.getSimpleName()))
                .isGreaterThan(authorization);
    }

    private MockHttpServletResponse call(MockHttpServletRequest request, Authentication authentication)
            throws ServletException, IOException {
        if (authentication != null) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    context);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            filterChain.doFilter(request, response, new MockFilterChain());
        } finally {
            SecurityContextHolder.clearContext();
        }
        return response;
    }

    /**
     * A POST with a valid CSRF token. Without one every POST would end in 403
     * before it reaches the passkey filters -- and the tests that expect a
     * refusal would then pass for the wrong reason. The request lives in the
     * application's servlet context so that {@code csrf()} finds the chain's
     * own token repository.
     */
    private MockHttpServletRequest post(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(context.getServletContext(), "POST", path);
        return csrf().postProcessRequest(request);
    }

    /**
     * Sent to the sign-in -- precisely that, not merely "not 200": a 403 from
     * a missing CSRF token is also not 200, and a refusal test would then
     * pass without the rule it is about ever being asked.
     */
    private static void assertSentToSignIn(MockHttpServletResponse response) {
        assertThat(response.getStatus()).as("redirected, not answered").isEqualTo(302);
        assertThat(response.getRedirectedUrl()).endsWith("/login");
    }

    private static String body(MockHttpServletResponse response) throws IOException {
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private <F extends Filter> F filterOf(Class<F> type) {
        return filterChain.getFilterChains().getFirst().getFilters().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }

    private static int indexOf(List<Filter> filters, String simpleName) {
        for (int i = 0; i < filters.size(); i++) {
            if (filters.get(i).getClass().getSimpleName().equals(simpleName)) {
                return i;
            }
        }
        throw new AssertionError("No filter " + simpleName + " in " + filters);
    }
}
