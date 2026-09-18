package de.zettsystems.identity.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class of the integration tests of this building block.
 *
 * <p>The container is started statically <strong>once</strong> and shared by
 * every test class. Deliberately not {@code @Testcontainers}/{@code @Container}
 * at class level: that starts one container per test class and pushes Docker to
 * its limits in no time.
 */
@SpringBootTest(classes = IdentityTestApplication.class)
public abstract class AbstractIdentityIntegrationTest {

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
}
