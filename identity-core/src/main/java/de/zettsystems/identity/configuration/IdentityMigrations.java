package de.zettsystems.identity.configuration;

import de.zettsystems.identity.values.IdentitySchema;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * Runs the building block's own Flyway migrations: schema {@code identity},
 * history table {@code identity.flyway_schema_history}, scripts from
 * {@code classpath:db/identity}.
 *
 * <p>Up to 0.7.x the scripts were appended to the <em>application's</em>
 * Flyway run, sharing its history under a reserved version space (V1_x for
 * the building block, V2_x upwards for the application). That held only as
 * long as no application ever drew a baseline above 1: a {@code B20} in the
 * application marks V1_1 to V1_5 as {@code BELOW_BASELINE}, and they never run
 * -- on a fresh database as on an existing one, {@code out-of-order} or not.
 * Two independently growing products cannot share one linear version line;
 * numbering conventions only postpone the day. Hence a history of the
 * building block's own, in a schema of its own.
 *
 * <p><strong>Order:</strong> this runs <em>before</em> the application's
 * Flyway. Flyway checks "schema not empty but no history" only for the
 * schemas it manages, so the application's run is unaffected by ours -- and
 * an application migration may reference {@code identity.auth_user}, for a
 * foreign key or for copying accounts over. The other way round would force
 * {@code baselineOnMigrate} on us and forbid exactly that.
 *
 * <p><strong>Installations from before 0.8.0</strong> have the tables in the
 * application's default schema and the versions 1.1 to 1.5 in the
 * application's history. {@link #migrate} recognises that layout -- an
 * {@code auth_user} table where the application lives, no history of ours yet
 * -- and moves it once: tables and sequences go to the {@code identity} schema
 * ({@code ALTER ... SET SCHEMA}, which keeps data, indexes and foreign keys
 * from application tables intact), our rows leave the application's history
 * (otherwise its validation stops on "applied migration not resolved
 * locally"), and our own history starts with a baseline at the version the
 * tables actually have. Everything runs in one transaction; a failure leaves
 * the old layout untouched and the application does not start, which is the
 * right outcome for a half-moved schema.
 *
 * <p>Requires PostgreSQL, like the migrations themselves, and a database user
 * allowed to create a schema -- the owner of the database is.
 */
public class IdentityMigrations {

    static final String LOCATION = "classpath:db/identity";
    static final String HISTORY_TABLE = "flyway_schema_history";

    private static final Logger LOG = LoggerFactory.getLogger(IdentityMigrations.class);

    /** The tables, in creation order; moved in this order too. */
    private static final List<String> TABLES =
            List.of("auth_user", "auth_role", "auth_role_authority", "auth_user_role", "auth_token");
    private static final List<String> SEQUENCES =
            List.of("auth_user_seq", "auth_role_seq", "auth_token_seq", "auth_user_role_seq");
    /** The scripts as they appear in the {@code script} column of a shared history. */
    private static final List<String> SCRIPTS = List.of(
            "V1_1__create_auth_tables.sql",
            "V1_2__display_name.sql",
            "V1_3__must_change_password.sql",
            "V1_4__account_locale.sql",
            "V1_5__role_scope.sql");

    /**
     * Migrates against the application's history under its default name, in
     * the schema the connection starts in. For applications that run Flyway
     * themselves and call this by hand.
     */
    public MigrateResult migrate(DataSource dataSource) {
        return migrate(dataSource, null, HISTORY_TABLE);
    }

    /**
     * Migrates, moving a pre-0.8.0 layout out of the application's schema and
     * history first if there is one.
     *
     * @param legacySchema    the schema the application's own tables live in
     *                        and where a pre-0.8.0 installation kept ours;
     *                        {@code null} for the connection's current schema
     * @param appHistoryTable the application's Flyway history table, whose
     *                        rows for our scripts are removed when moving
     */
    public MigrateResult migrate(DataSource dataSource, @Nullable String legacySchema, String appHistoryTable) {
        Optional<String> movedFrom = relocateLegacyLayout(dataSource, legacySchema, appHistoryTable);

        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations(LOCATION)
                .schemas(IdentitySchema.NAME)
                .defaultSchema(IdentitySchema.NAME)
                .createSchemas(true)
                .table(HISTORY_TABLE);
        movedFrom.ifPresent(version -> configuration
                // The moved tables already have this state; the history of
                // our own starts there and only later versions are applied.
                // No apostrophe in the description: Flyway writes the baseline
                // row as a literal statement, not with a bound parameter.
                .baselineOnMigrate(true)
                .baselineVersion(version)
                .baselineDescription("<< moved from shared history >>"));

        MigrateResult result = configuration.load().migrate();
        if (result.migrationsExecuted > 0) {
            LOG.info("Identity schema migrated: {} migration(s) applied, now at {}",
                    result.migrationsExecuted, result.targetSchemaVersion);
        }
        return result;
    }

    /**
     * @return the version the moved tables are at, or empty when there was
     *         nothing to move
     */
    private Optional<String> relocateLegacyLayout(DataSource dataSource, @Nullable String legacySchema,
                                                  String appHistoryTable) {
        try (Connection connection = dataSource.getConnection()) {
            String from = legacySchema != null ? legacySchema : connection.getSchema();
            if (from == null || IdentitySchema.NAME.equals(from)
                    || tableExists(connection, IdentitySchema.NAME, HISTORY_TABLE)
                    || !tableExists(connection, from, "auth_user")) {
                return Optional.empty();
            }
            String version = versionOf(connection, from);
            LOG.warn("Identity tables found in schema '{}' (layout before 0.8.0, at version {}): "
                    + "moving them to schema '{}' once", from, version, IdentitySchema.NAME);

            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA IF NOT EXISTS " + IdentitySchema.NAME);
                for (String table : TABLES) {
                    if (tableExists(connection, from, table)) {
                        statement.execute("ALTER TABLE " + qualified(from, table)
                                + " SET SCHEMA " + IdentitySchema.NAME);
                    }
                }
                for (String sequence : SEQUENCES) {
                    if (sequenceExists(connection, from, sequence)) {
                        statement.execute("ALTER SEQUENCE " + qualified(from, sequence)
                                + " SET SCHEMA " + IdentitySchema.NAME);
                    }
                }
                if (tableExists(connection, from, appHistoryTable)) {
                    int removed = removeOurRows(connection, from, appHistoryTable);
                    LOG.info("Removed {} row(s) of the identity migrations from {}",
                            removed, qualified(from, appHistoryTable));
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
            return Optional.of(version);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not move the identity tables to their own schema", e);
        }
    }

    /**
     * Reads the version off the tables rather than off the application's
     * history: the history may be named differently or already cleaned up,
     * the columns are always there.
     */
    private static String versionOf(Connection connection, String schema) throws SQLException {
        if (columnExists(connection, schema, "auth_user_role", "scope_type")) {
            return "1.5";
        }
        if (columnExists(connection, schema, "auth_user", "locale")) {
            return "1.4";
        }
        if (columnExists(connection, schema, "auth_user", "must_change_password")) {
            return "1.3";
        }
        if (columnExists(connection, schema, "auth_user", "display_name")) {
            return "1.2";
        }
        return "1.1";
    }

    private static int removeOurRows(Connection connection, String schema, String table) throws SQLException {
        String placeholders = String.join(", ", SCRIPTS.stream().map(script -> "?").toList());
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + qualified(schema, table) + " WHERE script IN (" + placeholders + ")")) {
            for (int i = 0; i < SCRIPTS.size(); i++) {
                statement.setString(i + 1, SCRIPTS.get(i));
            }
            return statement.executeUpdate();
        }
    }

    private static boolean tableExists(Connection connection, String schema, String table) throws SQLException {
        return exists(connection, "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?",
                schema, table);
    }

    private static boolean sequenceExists(Connection connection, String schema, String sequence) throws SQLException {
        return exists(connection,
                "SELECT 1 FROM information_schema.sequences WHERE sequence_schema = ? AND sequence_name = ?",
                schema, sequence);
    }

    private static boolean columnExists(Connection connection, String schema, String table, String column)
            throws SQLException {
        return exists(connection, "SELECT 1 FROM information_schema.columns"
                + " WHERE table_schema = ? AND table_name = ? AND column_name = ?", schema, table, column);
    }

    private static boolean exists(Connection connection, String sql, String... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setString(i + 1, parameters[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /** Quoted, because the application's history table name is not ours to trust unquoted. */
    private static String qualified(String schema, String name) {
        return "\"" + schema.replace("\"", "\"\"") + "\".\"" + name.replace("\"", "\"\"") + "\"";
    }
}
