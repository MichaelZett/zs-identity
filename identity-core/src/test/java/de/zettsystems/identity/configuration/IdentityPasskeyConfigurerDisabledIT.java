package de.zettsystems.identity.configuration;

import de.zettsystems.identity.testsupport.PostgresTestImage;
import de.zettsystems.identity.values.IdentityPaths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same chain with {@code zs.identity.passkeys.enabled} left at its
 * default: the configurer is in the code, but nothing of it is in the chain.
 * That is what lets an application keep one security configuration across
 * environments and decide per environment through the property.
 */
@SpringBootTest(classes = IdentityPasskeyConfigurerIT.PasskeyApplication.class)
class IdentityPasskeyConfigurerDisabledIT {

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

    @Autowired
    private FilterChainProxy filterChain;

    @Test
    void withPasskeysOffTheChainKnowsNothingOfThem() throws Exception {
        assertThat(filterChain.getFilterChains().getFirst().getFilters())
                .noneMatch(WebAuthnAuthenticationFilter.class::isInstance);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filterChain.doFilter(new MockHttpServletRequest("POST", IdentityPaths.PASSKEY_AUTHENTICATION_OPTIONS),
                response, new MockFilterChain());

        assertThat(response.getStatus()).as("no endpoint, no permit rule: not open").isNotEqualTo(200);
    }
}
