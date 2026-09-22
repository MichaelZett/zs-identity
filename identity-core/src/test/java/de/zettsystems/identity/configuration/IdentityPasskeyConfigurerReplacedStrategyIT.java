package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.IdentityUserDetails;
import de.zettsystems.identity.application.UserAccountService;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.PostgresTestImage;
import de.zettsystems.identity.values.IdentityPaths;
import de.zettsystems.identity.values.UserAccountDto;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsFilter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chain of {@link IdentityPasskeyConfigurerIT}, but with the
 * {@code SecurityContextHolderStrategy} replaced by one of the application's
 * -- which is what a Vaadin application does, and what no test here had ever
 * done.
 *
 * <p>Why it needs a test of its own: every filter in {@link
 * IdentityPasskeyConfigurerIT} shares the one static strategy, so a filter
 * that reads the static one and a filter that reads the bean cannot be told
 * apart there. Vaadin's {@code VaadinAwareSecurityContextHolderStrategy}
 * keeps the context in a {@code ThreadLocal} of its own; whoever still reads
 * the static default then sees nothing, whatever the session says. That is
 * how registering a passkey broke in {@code terminplanung-halle} on
 * 2026-09-22 -- {@code POST /webauthn/register/options} answered {@code 400}
 * with an empty body and no log line, because Spring's
 * {@link PublicKeyCredentialCreationOptionsFilter} has no setter for the
 * strategy and fails its own {@code authenticated()} check that way.
 *
 * <p>The strategy here does no more than Vaadin's does for this purpose: it
 * holds the context somewhere the static default cannot see. Vaadin itself
 * has no place in {@code identity-core}.
 */
@SpringBootTest(classes = {IdentityPasskeyConfigurerIT.PasskeyApplication.class,
        IdentityPasskeyConfigurerReplacedStrategyIT.ReplacedStrategy.class}, properties = {
        "zs.identity.passkeys.enabled=true",
        "zs.identity.passkeys.rp-id=localhost",
        "zs.identity.passkeys.rp-name=Test",
        "zs.identity.passkeys.allowed-origins=http://localhost:8080"})
class IdentityPasskeyConfigurerReplacedStrategyIT {

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
     * What an application adds when it brings its own strategy; Vaadin adds
     * exactly this bean. {@code @TestConfiguration} rather than
     * {@code @Configuration}: the application of {@link
     * IdentityPasskeyConfigurerIT} scans this very package, and a plain
     * {@code @Configuration} here would quietly put the strategy into that
     * test's chain as well.
     */
    @TestConfiguration
    static class ReplacedStrategy {

        @Bean
        SecurityContextHolderStrategy securityContextHolderStrategy() {
            return new OwnThreadLocalStrategy();
        }
    }

    /**
     * A strategy with a {@code ThreadLocal} of its own -- the one trait of
     * Vaadin's that matters here: what is written through it is invisible to
     * anyone reading the static default.
     */
    static class OwnThreadLocalStrategy implements SecurityContextHolderStrategy {

        private final ThreadLocal<SecurityContext> contexts = new ThreadLocal<>();

        @Override
        public void clearContext() {
            contexts.remove();
        }

        @Override
        public SecurityContext getContext() {
            SecurityContext context = contexts.get();
            if (context == null) {
                context = createEmptyContext();
                contexts.set(context);
            }
            return context;
        }

        @Override
        public void setContext(SecurityContext context) {
            contexts.set(context);
        }

        @Override
        public SecurityContext createEmptyContext() {
            return new SecurityContextImpl();
        }
    }

    @Autowired
    private FilterChainProxy filterChain;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private SecurityContextHolderStrategy applicationStrategy;

    private UserAccountDto anna;

    @BeforeEach
    void anAccount() {
        userRepository.deleteAll();
        anna = userAccountService.createAccount("anna@example.com", "ein-langes-passwort", "Anna", "Beispiel", true);
    }

    /** The bug as the browser saw it: a signed-in session, and still no challenge. */
    @Test
    void aFreshSignInGetsARegistrationChallenge() throws Exception {
        IdentityUserDetails user = (IdentityUserDetails) userDetailsService.loadUserByUsername(anna.email());
        Authentication fresh = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());

        MockHttpServletResponse response = call(IdentityPaths.PASSKEY_REGISTRATION_OPTIONS, fresh);

        assertThat(response.getStatus())
                .as("400 with an empty body is the filter reading a strategy nobody writes to")
                .isEqualTo(200);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("\"challenge\"");
    }

    /** The same thing said about the cause, so that a red test names it. */
    @Test
    void theCreationOptionsFilterReadsTheApplicationsStrategy() {
        Filter filter = filterChain.getFilterChains().getFirst().getFilters().stream()
                .filter(PublicKeyCredentialCreationOptionsFilter.class::isInstance)
                .findFirst()
                .orElseThrow();

        assertThat(ReflectionTestUtils.getField(filter, "securityContextHolderStrategy"))
                .as("the strategy of the application, not the static one the constructor picked up")
                .isSameAs(applicationStrategy);
    }

    /** The static strategy is left as it was found -- the swap in the configurer puts it back. */
    @Test
    void theStaticStrategyIsNotTheApplicationsAfterTheChainIsBuilt() {
        assertThat(SecurityContextHolder.getContextHolderStrategy())
                .as("nothing installed the bean globally here; only Vaadin would")
                .isNotSameAs(applicationStrategy);
    }

    private MockHttpServletResponse call(String path, Authentication authentication)
            throws ServletException, java.io.IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        SecurityContext context = new SecurityContextImpl(authentication);
        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            filterChain.doFilter(request, response, new MockFilterChain());
        } finally {
            applicationStrategy.clearContext();
            SecurityContextHolder.clearContext();
        }
        return response;
    }
}
