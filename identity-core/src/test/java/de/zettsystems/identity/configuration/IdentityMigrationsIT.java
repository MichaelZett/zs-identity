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
import java.util.Map;
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
    /**
     * Makes Flyway ignore the baseline, so that the V chain runs: baseline
     * scripts get a prefix nobody uses. A setting of Flyway's baseline
     * extension, not of the fluent API.
     */
    private static final Map<String, String> CHAIN_ONLY =
            Map.of("flyway.baselineMigrationPrefix", "NOT_A_BASELINE_");

    private static final List<String> TABLES =
            List.of("auth_user", "auth_role", "auth_role_authority", "auth_user_role", "auth_token", "auth_passkey",
                    "auth_external_identity");

    static {
        POSTGRES.start();
    }

    /** Since 1.0.0 a fresh database takes the baseline, one script instead of six. */
    @Test
    void aFreshDatabaseGetsTheSchemaThroughTheBaseline() {
        DataSource dataSource = freshDatabase();

        MigrateResult first = new IdentityMigrations().migrate(dataSource);
        MigrateResult second = new IdentityMigrations().migrate(dataSource);

        assertThat(first.migrationsExecuted).as("the baseline B1_6 and V1_7, V1_8 on top").isEqualTo(3);
        assertThat(second.migrationsExecuted).as("the second run finds nothing to do").isZero();
        assertThat(tablesIn(dataSource, "identity")).containsExactlyInAnyOrderElementsOf(withHistory(TABLES));
        assertThat(tablesIn(dataSource, "public")).isEmpty();
        assertThat(versionsIn(dataSource, "identity", "flyway_schema_history")).containsExactly("1.6", "1.7", "1.8");
    }

    /**
     * The baseline has to be exactly what the V chain arrives at -- columns
     * and their order, types, defaults, constraints, indexes, sequences --
     * or fresh installations and upgraded ones drift apart for good.
     */
    @Test
    void theBaselineIsExactlyWhatTheChainArrivesAt() {
        DataSource viaBaseline = freshDatabase();
        new IdentityMigrations().migrate(viaBaseline);
        DataSource viaChain = freshDatabase();
        Flyway.configure()
                .dataSource(viaChain)
                .locations(IdentityMigrations.LOCATION)
                .schemas("identity")
                .defaultSchema("identity")
                .createSchemas(true)
                .configuration(CHAIN_ONLY)
                .load()
                .migrate();

        assertThat(versionsIn(viaChain, "identity", "flyway_schema_history"))
                .as("the chain really ran")
                .containsExactly("1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8");
        assertThat(schemaOf(viaBaseline)).isEqualTo(schemaOf(viaChain));

        // Every installation from before 1.0.0 looks like viaChain. Its first
        // start with the baseline on the classpath must neither run anything
        // nor fail Flyway's validation.
        MigrateResult upgrade = new IdentityMigrations().migrate(viaChain);
        assertThat(upgrade.migrationsExecuted).isZero();
        assertThat(versionsIn(viaChain, "identity", "flyway_schema_history"))
                .containsExactly("1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8");
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

        assertThat(moved.migrationsExecuted).as("1.5 is the baseline; 1.6 to 1.8 are applied on top").isEqualTo(3);
        assertThat(again.migrationsExecuted).isZero();
        assertThat(tablesIn(dataSource, "identity")).containsExactlyInAnyOrderElementsOf(withHistory(TABLES));
        assertThat(tablesIn(dataSource, "public"))
                .as("only the application's history stays behind")
                .containsExactly("flyway_schema_history");
        assertThat(sequencesIn(dataSource, "identity"))
                .containsExactlyInAnyOrder("auth_user_seq", "auth_role_seq", "auth_token_seq", "auth_user_role_seq",
                        "auth_passkey_seq", "auth_external_identity_seq");
        assertThat(jdbc.queryForObject("select display_name from identity.auth_user where id = 1", String.class))
                .isEqualTo("Anna");
        assertThat(versionsIn(dataSource, "identity", "flyway_schema_history"))
                .as("our history starts with the baseline at the version the tables have")
                .containsExactly("1.5", "1.6", "1.7", "1.8");
        assertThat(versionsIn(dataSource, "public", "flyway_schema_history"))
                .as("the application's history keeps its own rows and loses ours")
                .containsExactly("2.1");
    }

    /** An installation that stopped at 1.3 gets 1.4 to 1.8 after the move. */
    @Test
    void anOlderInstallationGetsTheRemainingMigrationsAfterTheMove() {
        DataSource dataSource = freshDatabase();
        legacyLayout(dataSource, "1.3");

        MigrateResult moved = new IdentityMigrations().migrate(dataSource, "public", "flyway_schema_history");

        assertThat(moved.migrationsExecuted).isEqualTo(5);
        assertThat(versionsIn(dataSource, "identity", "flyway_schema_history"))
                .containsExactly("1.3", "1.4", "1.5", "1.6", "1.7", "1.8");
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
     * the old auto-configuration did it -- through the V chain, because the
     * baseline did not exist then. Left to itself, Flyway would take the
     * baseline and build 1.6 whatever the target.
     */
    private static void legacyLayout(DataSource dataSource, String upTo) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(IdentityMigrations.LOCATION)
                .target(upTo)
                .configuration(CHAIN_ONLY)
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

    /** Everything about the identity schema a migration can change, as comparable lines. */
    private static List<String> schemaOf(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Stream<String> columns = jdbc.queryForList("""
                select concat_ws(' | ', table_name, ordinal_position, column_name, data_type,
                                 character_maximum_length, datetime_precision, is_nullable, column_default)
                from information_schema.columns
                where table_schema = 'identity' and table_name <> 'flyway_schema_history'
                """, String.class).stream();
        Stream<String> constraints = jdbc.queryForList("""
                select concat_ws(' | ', c.conrelid::regclass::text, c.conname, pg_get_constraintdef(c.oid))
                from pg_constraint c join pg_namespace n on n.oid = c.connamespace
                where n.nspname = 'identity' and c.conrelid::regclass::text not like '%flyway_schema_history'
                """, String.class).stream();
        Stream<String> indexes = jdbc.queryForList("""
                select concat_ws(' | ', indexname, indexdef) from pg_indexes
                where schemaname = 'identity' and tablename <> 'flyway_schema_history'
                """, String.class).stream();
        Stream<String> sequences = jdbc.queryForList("""
                select concat_ws(' | ', sequence_name, start_value, increment) from information_schema.sequences
                where sequence_schema = 'identity'
                """, String.class).stream();
        return Stream.of(columns, constraints, indexes, sequences).flatMap(lines -> lines).sorted().toList();
    }

    private static List<String> versionsIn(DataSource dataSource, String schema, String table) {
        return new JdbcTemplate(dataSource).query(
                "select version from " + schema + "." + table + " where version is not null order by installed_rank",
                (row, i) -> row.getString("version"));
    }
}
