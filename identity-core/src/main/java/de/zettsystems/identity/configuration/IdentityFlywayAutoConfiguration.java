package de.zettsystems.identity.configuration;

import de.zettsystems.identity.values.IdentityProperties;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.Arrays;

/**
 * Runs the building block's migrations when the application's Flyway is set
 * up -- before it, against the same data source.
 *
 * <p>The hook is a {@link FlywayConfigurationCustomizer}: Spring Boot calls the
 * customizers while it builds the application's {@code Flyway} bean, after it
 * has resolved the data source ({@code @FlywayDataSource}, {@code spring.flyway.url}
 * or the primary one) and before anything migrates. That is exactly the
 * moment we need: the data source the application will migrate with is known,
 * and everything that waits for the application's migrations -- the JPA
 * {@code EntityManagerFactory} among them -- waits for ours as well, because
 * ours are finished before the application's bean even exists. No
 * {@code @DependsOn} and no ordering of initializers is needed.
 *
 * <p>The customizer also removes {@code classpath:db/identity} from the
 * application's locations if an application still lists it from before
 * 0.8.0; otherwise the application's run would find the scripts a second
 * time.
 *
 * <p>Applies only when the application uses Flyway at all: the building block
 * has no Flyway dependency of its own ({@code compileOnly}). With
 * {@code spring.flyway.enabled=false} or {@code zs.identity.migrations.enabled=false}
 * nothing runs here, and the application calls {@link IdentityMigrations#migrate}
 * itself.
 */
@AutoConfiguration(before = FlywayAutoConfiguration.class, after = IdentityAutoConfiguration.class)
@ConditionalOnClass(FlywayConfigurationCustomizer.class)
public class IdentityFlywayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    IdentityMigrations identityMigrations() {
        return new IdentityMigrations();
    }

    /**
     * The properties come through a provider: without JPA on the classpath
     * {@link IdentityAutoConfiguration} is inactive and there is no bean --
     * then the migrations simply run, as they do by default.
     */
    @Bean
    FlywayConfigurationCustomizer identityMigrationsCustomizer(IdentityMigrations migrations,
                                                               ObjectProvider<IdentityProperties> properties) {
        IdentityProperties configured = properties.getIfAvailable();
        boolean enabled = configured == null || configured.migrations().enabled();
        return new IdentityMigrationsCustomizer(migrations, enabled);
    }

    /** Package-visible so that the test can drive it with a configuration of its own. */
    static final class IdentityMigrationsCustomizer implements FlywayConfigurationCustomizer {

        private final IdentityMigrations migrations;
        private final boolean enabled;

        IdentityMigrationsCustomizer(IdentityMigrations migrations, boolean enabled) {
            this.migrations = migrations;
            this.enabled = enabled;
        }

        @Override
        public void customize(FluentConfiguration configuration) {
            stripOurLocation(configuration);
            if (!enabled) {
                return;
            }
            DataSource dataSource = configuration.getDataSource();
            if (dataSource == null) {
                throw new IllegalStateException("Flyway has no data source; the identity migrations cannot run. "
                        + "Set zs.identity.migrations.enabled=false and call IdentityMigrations#migrate yourself.");
            }
            migrations.migrate(dataSource, applicationSchema(configuration), configuration.getTable());
        }

        private static void stripOurLocation(FluentConfiguration configuration) {
            String[] locations = Arrays.stream(configuration.getLocations())
                    .map(Location::getDescriptor)
                    .filter(location -> !IdentityMigrations.LOCATION.equals(location))
                    .toArray(String[]::new);
            if (locations.length != configuration.getLocations().length) {
                configuration.locations(locations);
            }
        }

        /**
         * Where the application keeps its tables -- and where an installation
         * from before 0.8.0 kept ours. Flyway itself falls back to the
         * connection's schema when nothing is configured; so do we.
         */
        private static @Nullable String applicationSchema(FluentConfiguration configuration) {
            if (configuration.getDefaultSchema() != null) {
                return configuration.getDefaultSchema();
            }
            String[] schemas = configuration.getSchemas();
            return schemas.length > 0 ? schemas[0] : null;
        }
    }
}
