package de.zettsystems.identity.configuration;

import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.IdentityTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The first start of an application with 0.8.0 against a <strong>copy</strong>
 * of a real database from before -- the whole Spring Boot path this time, not
 * the migrator alone: Boot builds the application's Flyway, our customizer
 * moves and migrates, the application's Flyway validates its cleaned history,
 * Hibernate validates the entities against {@code identity.*}, the role
 * synchroniser writes. Same environment variables as
 * {@link LegacyLayoutRehearsalIT}; skipped without {@code PROBE_JDBC_URL}.
 * Run it on a freshly restored copy: after {@code LegacyLayoutRehearsalIT} the
 * move has already happened and this proves less.
 */
class LegacyLayoutFirstStartIT {

    @Test
    void theFirstStartMovesAndThenRunsLikeAnyOther() {
        String url = System.getenv("PROBE_JDBC_URL");
        assumeTrue(url != null, "no PROBE_JDBC_URL set");
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", url);
        properties.put("spring.datasource.username", System.getenv("PROBE_USER"));
        properties.put("spring.datasource.password", System.getenv("PROBE_PASSWORD"));
        String appMigrations = System.getenv("PROBE_APP_MIGRATIONS");
        if (appMigrations != null) {
            properties.put("spring.flyway.locations", "filesystem:" + appMigrations);
        }

        long users;
        long usersAfterFirstStart;
        try (ConfigurableApplicationContext context = start(properties)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            users = jdbc.queryForObject("select count(*) from identity.auth_user", Long.class);
            usersAfterFirstStart = context.getBean(UserAccountRepository.class).count();
            System.out.println("PROBE first start: identity history "
                    + jdbc.queryForList("select version, type from identity.flyway_schema_history order by installed_rank")
                    + ", auth_* left in public: " + jdbc.queryForList(
                    "select table_name from information_schema.tables where table_schema='public' and table_name like 'auth\\_%'",
                    String.class));
        }
        // The second start is an ordinary one: nothing to move, nothing to migrate.
        try (ConfigurableApplicationContext context = start(properties)) {
            long usersAfterSecondStart = context.getBean(UserAccountRepository.class).count();
            assertThat(usersAfterSecondStart).isEqualTo(users);
        }

        assertThat(usersAfterFirstStart).as("Hibernate reads the moved accounts through identity.*").isEqualTo(users);
        System.out.println("PROBE first start ok, accounts: " + users);
    }

    private static ConfigurableApplicationContext start(Map<String, Object> properties) {
        return new SpringApplicationBuilder(IdentityTestApplication.class)
                .web(WebApplicationType.NONE)
                .properties(properties)
                .run();
    }
}
