package de.zettsystems.identity.configuration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Rehearses the move of an installation from before 0.8.0 against a
 * <strong>copy</strong> of a real application database -- the step every
 * embedding application should take once before its first start with 0.8.0.
 *
 * <p>Skipped unless the environment names the copy:
 * <pre>
 * PROBE_JDBC_URL=jdbc:postgresql://localhost:5433/terminplanung_probe
 * PROBE_USER=... PROBE_PASSWORD=...
 * PROBE_APP_MIGRATIONS=C:/.../src/main/resources/db/migration/app   (optional)
 * ./gradlew :identity-core:test --tests '*LegacyLayoutRehearsalIT' -i
 * </pre>
 * With {@code PROBE_APP_MIGRATIONS} set, the application's own Flyway validates
 * the cleaned history afterwards -- the proof that the application would start.
 * Never point this at a live database: it changes what it touches.
 */
class LegacyLayoutRehearsalIT {

    @Test
    void rehearsesTheMoveOnACopyOfARealDatabase() {
        String url = System.getenv("PROBE_JDBC_URL");
        assumeTrue(url != null, "no PROBE_JDBC_URL set");
        DataSource dataSource = new DriverManagerDataSource(url,
                System.getenv("PROBE_USER"), System.getenv("PROBE_PASSWORD"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long usersBefore = jdbc.queryForObject("select count(*) from public.auth_user", Long.class);
        long rolesBefore = jdbc.queryForObject("select count(*) from public.auth_user_role", Long.class);
        long appRowsBefore = jdbc.queryForObject(
                "select count(*) from public.flyway_schema_history where script not like 'V1\\_%'", Long.class);

        MigrateResult moved = new IdentityMigrations().migrate(dataSource, "public", "flyway_schema_history");
        MigrateResult again = new IdentityMigrations().migrate(dataSource, "public", "flyway_schema_history");

        System.out.println("PROBE executed on move: " + moved.migrationsExecuted + ", second run: " + again.migrationsExecuted);
        System.out.println("PROBE identity history: " + jdbc.queryForList(
                "select version, description, type from identity.flyway_schema_history order by installed_rank"));
        System.out.println("PROBE tables left in public named auth_*: " + jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema='public' and table_name like 'auth\\_%'",
                String.class));
        assertThat(jdbc.queryForObject("select count(*) from identity.auth_user", Long.class)).isEqualTo(usersBefore);
        assertThat(jdbc.queryForObject("select count(*) from identity.auth_user_role", Long.class)).isEqualTo(rolesBefore);
        assertThat(jdbc.queryForObject(
                "select count(*) from public.flyway_schema_history where script like 'V1\\_%'", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from public.flyway_schema_history where script not like 'V1\\_%'", Long.class))
                .isEqualTo(appRowsBefore);
        // The sequences must keep their position: the next id must not collide.
        System.out.println("PROBE next auth_user id: " + jdbc.queryForObject("select nextval('identity.auth_user_seq')", Long.class)
                + ", max id: " + jdbc.queryForObject("select max(id) from identity.auth_user", Long.class));

        String appMigrations = System.getenv("PROBE_APP_MIGRATIONS");
        if (appMigrations != null) {
            Flyway app = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("filesystem:" + appMigrations)
                    .load();
            app.validate();
            MigrationInfo[] pending = app.info().pending();
            System.out.println("PROBE application Flyway validates; pending: " + Arrays.toString(pending));
            assertThat(pending).isEmpty();
        }
    }
}
