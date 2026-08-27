package de.zettsystems.identity.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Basis der Integrationstests dieses Bausteins.
 *
 * <p>Der Container wird <strong>einmal</strong> statisch gestartet und von allen
 * Testklassen geteilt. Bewusst nicht {@code @Testcontainers}/{@code @Container}
 * auf Klassenebene: Das startet einen Container pro Testklasse und bringt Docker
 * schnell an seine Grenzen.
 */
@SpringBootTest(classes = IdentityTestApplication.class)
public abstract class AbstractIdentityIntegrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresTestImage.resolve())
            .withDatabaseName("identity")
            .withUsername("identity")
            .withPassword("identity")
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
}
