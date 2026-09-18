package de.zettsystems.identity.configuration;

import de.zettsystems.identity.testsupport.PostgresTestImage;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The building block's own Flyway run, against real PostgreSQL: a fresh
 * database, the move of an installation from before 0.8.0, and the case that
 * forced the change -- an application whose own history carries a baseline.
 *
 * <p>Every test gets a database of its own on the shared container: the
 * scenarios differ in what is already there, and a shared database would
 * make the tests depend on their order.
 */
class IdentityMigrationsIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresTestImage.resolve())
            .withDatabaseName("identity")
            .withUsername("app")
            .withPassword("app")
            .withReuse(true);
    private static final AtomicInteger DATABASES = new AtomicInteger();

    private static final List<String> TABLES =
            List.of("auth_user", "auth_role", "auth_role_authority", "auth_user_role", "auth_token");

    static {
        POSTGRES.start();
    }

    @Test
    void aFreshDatabaseGetsTheSchemaAndAllMigrations() {
        DataSource dataSource = freshDatabase();

        MigrateResult first = new IdentityMigrations().migrate(dataSource);
        MigrateResult second = new IdentityMigrations().migrate(dataSource);

        assertThat(first.migrationsExecuted).isEqualTo(5);
        assertThat(second.migrationsExecuted).as("the second run finds nothing to do").isZero();
        assertThat(tablesIn(dataSource, "identity")).containsExactlyInAnyOrderElementsOf(withHistory(TABLES));
        assertThat(tablesIn(dataSource, "public")).isEmpty();
        assertThat(versionsIn(dataSource, "identity", "flyway_schema_history"))
                .containsExactly("1.1", "1.2", "1.3", "1.4", "1.5");
    }

    /**
     * The layout every installation before 0.8.0 has: our tables next to the
     * application's, our versions in the application's history.
     */
    @Test
    void anInstallationFromBeforeIsMovedOnceWithItsData() {
        DataSource dataSource = freshDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        legacyLayout(dataSource, "1.5");
        jdbc.update("""
                insert into auth_user (id, email, display_name, enabled, email_verified, created_at)
                values (1, 'anna@example.com', 'Anna', true, true, now())
                """);
        jdbc.update("""
                insert into flyway_schema_history
                    (installed_rank, version, description, type, script, checksum, installed_by,
                     installed_on, execution_time, success)
                values (99, '2.1', 'create schema', 'SQL', 'V2_1__create_schema.sql', 0, 'test', now(), 1, true)
                """);

        MigrateResult moved = new IdentityMigrations().migrate(dataSource, "public", "flyway_schema_history");
        MigrateResult again = new IdentityMigrations().migrate(dataSource, "public", "flyway_schema_history");

        assertThat(moved.migrationsExecuted).as("1.5 is the baseline, nothing above it yet").isZero();
        assertThat(again.migrationsExecuted).isZero();
        assertThat(tablesIn(dataSource, "identity")).containsExactlyInAnyOrderElementsOf(withHistory(TABLES));
        assertThat(tablesIn(dataSource, "public"))
                .as("only the application's history stays behind")
                .containsExactly("flyway_schema_history");
        assertThat(sequencesIn(dataSource, "identity"))
                .containsExactlyInAnyOrder("auth_user_seq", "auth_role_seq", "auth_token_seq", "auth_user_role_seq");
        assertThat(jdbc.queryForObject("select display_name from identity.auth_user where id = 1", String.class))
                .isEqualTo("Anna");
        assertThat(versionsIn(dataSource, "identity", "flyway_schema_history"))
                .as("our history starts with the baseline at the version the tables have")
                .containsExactly("1.5");
        assertThat(versionsIn(dataSource, "public", "flyway_schema_history"))
                .as("the application's history keeps its own rows and loses ours")
                .containsExactly("2.1");
    }

    /** An installation that stopped at 1.3 gets 1.4 and 1.5 after the move. */
    @Test
    void anOlderInstallationGetsTheRemainingMigrationsAfterTheMove() {
        DataSource dataSource = freshDatabase();
        legacyLayout(dataSource, "1.3");

        MigrateResult moved = new IdentityMigrations().migrate(dataSource, "public", "flyway_schema_history");

        assertThat(moved.migrationsExecuted).isEqualTo(2);
        assertThat(versionsIn(dataSource, "identity", "flyway_schema_history"))
                .containsExactly("1.3", "1.4", "1.5");
        assertThat(columnsOf(dataSource, "identity", "auth_user")).contains("locale");
        assertThat(columnsOf(dataSource, "identity", "auth_user_role")).contains("scope_type", "scope_id");
    }

    /**
     * The case that forced the change: an application with a baseline of its
     * own. With one shared history its {@code B20} marked our V1_x as below the
     * baseline and they never ran. Now the application's run neither sees nor
     * needs them -- and may even reference our table.
     */
    @Test
    void anApplicationWithABaselineOfItsOwnIsUnaffected() {
        DataSource dataSource = freshDatabase();
        new IdentityMigrations().migrate(dataSource);

        MigrateResult application = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/applike")
                .load()
                .migrate();

        assertThat(application.migrationsExecuted).as("B20 and V21").isEqualTo(2);
        assertThat(tablesIn(dataSource, "public"))
                .containsExactlyInAnyOrder("flyway_schema_history", "app_setting", "user_preference");
        assertThat(tablesIn(dataSource, "identity")).containsExactlyInAnyOrderElementsOf(withHistory(TABLES));
    }

    /** Our tables plus our history table -- what a migrated identity schema holds. */
    private static List<String> withHistory(List<String> tables) {
        return Stream.concat(tables.stream(), Stream.of("flyway_schema_history")).toList();
    }

    /**
     * Builds the pre-0.8.0 layout the honest way: the scripts run through
     * Flyway into {@code public} with the application's history, exactly as
     * the old auto-configuration did it.
     */
    private static void legacyLayout(DataSource dataSource, String upTo) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(IdentityMigrations.LOCATION)
                .target(upTo)
                .load()
                .migrate();
    }

    private static DataSource freshDatabase() {
        String name = "migrations_" + DATABASES.incrementAndGet();
        new JdbcTemplate(dataSource(POSTGRES.getDatabaseName())).execute("create database " + name);
        return dataSource(name);
    }

    private static DataSource dataSource(String database) {
        String url = POSTGRES.getJdbcUrl().replaceAll("/" + POSTGRES.getDatabaseName() + "(\\?|$)", "/" + database + "$1");
        return new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static List<String> tablesIn(DataSource dataSource, String schema) {
        return new JdbcTemplate(dataSource).queryForList(
                "select table_name from information_schema.tables where table_schema = ? order by table_name",
                String.class, schema);
    }

    private static List<String> sequencesIn(DataSource dataSource, String schema) {
        return new JdbcTemplate(dataSource).queryForList(
                "select sequence_name from information_schema.sequences where sequence_schema = ?",
                String.class, schema);
    }

    private static List<String> columnsOf(DataSource dataSource, String schema, String table) {
        return new JdbcTemplate(dataSource).queryForList(
                "select column_name from information_schema.columns where table_schema = ? and table_name = ?",
                String.class, schema, table);
    }

    private static List<String> versionsIn(DataSource dataSource, String schema, String table) {
        return new JdbcTemplate(dataSource).query(
                "select version from " + schema + "." + table + " where version is not null order by installed_rank",
                (row, i) -> row.getString("version"));
    }
}
