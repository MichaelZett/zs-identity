package de.zettsystems.identity.configuration;

import de.zettsystems.identity.configuration.IdentityFlywayAutoConfiguration.IdentityMigrationsCustomizer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * The customizer is the seam between the application's Flyway and ours; what
 * it hands over is pinned down here without a database.
 */
class IdentityFlywayAutoConfigurationTest {

    private final RecordingMigrations migrations = new RecordingMigrations();
    private final DataSource dataSource = mock(DataSource.class);

    @Test
    void runsOurMigrationsAgainstTheApplicationsDataSourceAndHistory() {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .defaultSchema("app")
                .table("app_history");

        new IdentityMigrationsCustomizer(migrations, true).customize(configuration);

        assertThat(migrations.calls).singleElement().satisfies(call -> {
            assertThat(call.dataSource()).isSameAs(dataSource);
            assertThat(call.legacySchema()).isEqualTo("app");
            assertThat(call.appHistoryTable()).isEqualTo("app_history");
        });
    }

    /** Without a default schema Flyway uses the connection's; we leave that to it too. */
    @Test
    void withoutAConfiguredSchemaTheConnectionDecides() {
        new IdentityMigrationsCustomizer(migrations, true)
                .customize(Flyway.configure().dataSource(dataSource));

        assertThat(migrations.calls).singleElement()
                .satisfies(call -> assertThat(call.legacySchema()).isNull());
    }

    @Test
    void theFirstOfSeveralSchemasCounts() {
        new IdentityMigrationsCustomizer(migrations, true)
                .customize(Flyway.configure().dataSource(dataSource).schemas("first", "second"));

        assertThat(migrations.calls).singleElement()
                .satisfies(call -> assertThat(call.legacySchema()).isEqualTo("first"));
    }

    /**
     * An application from before 0.8.0 may still list our location; the
     * application's run must not find the scripts a second time.
     */
    @Test
    void removesOurLocationFromTheApplicationsRun() {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", IdentityMigrations.LOCATION);

        new IdentityMigrationsCustomizer(migrations, true).customize(configuration);

        assertThat(configuration.getLocations())
                .extracting(location -> location.getDescriptor())
                .containsExactly("classpath:db/migration");
    }

    @Test
    void leavesTheApplicationsLocationsAloneOtherwise() {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/app");

        new IdentityMigrationsCustomizer(migrations, true).customize(configuration);

        assertThat(configuration.getLocations())
                .extracting(location -> location.getDescriptor())
                .containsExactly("classpath:db/migration/app");
    }

    /** Switched off: the location still goes, but nothing runs -- the application does that itself. */
    @Test
    void switchedOffItOnlyCleansTheLocations() {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", IdentityMigrations.LOCATION);

        new IdentityMigrationsCustomizer(migrations, false).customize(configuration);

        assertThat(migrations.calls).isEmpty();
        assertThat(configuration.getLocations()).hasSize(1);
    }

    /** A Flyway without a data source cannot happen through Spring Boot; if it does, say so rather than NPE. */
    @Test
    void refusesToRunWithoutADataSource() {
        assertThatThrownBy(() -> new IdentityMigrationsCustomizer(migrations, true).customize(Flyway.configure()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zs.identity.migrations.enabled=false");
    }

    private record Call(DataSource dataSource, @Nullable String legacySchema, String appHistoryTable) {
    }

    private static final class RecordingMigrations extends IdentityMigrations {
        private final List<Call> calls = new ArrayList<>();

        @Override
        public MigrateResult migrate(DataSource dataSource, @Nullable String legacySchema, String appHistoryTable) {
            calls.add(new Call(dataSource, legacySchema, appHistoryTable));
            return mock(MigrateResult.class);
        }
    }
}
