package de.zettsystems.identity.configuration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;

/**
 * Appends the building block's migration location to the application's Flyway
 * configuration.
 *
 * <p>Without this, every embedding application would have to add
 * {@code classpath:db/identity} to {@code spring.flyway.locations} by hand,
 * and whoever forgets gets a startup failure reading "Schema validation:
 * missing table [auth_role]" whose cause has to be hunted down. The building
 * block now brings its own tables along.
 *
 * <p>This only applies when the application uses Flyway at all. The building
 * block itself has no Flyway dependency (see {@code compileOnly} in the build
 * script); whoever uses a different migration tool copies the SQL from
 * {@code db/identity} over there instead.
 */
@AutoConfiguration(before = FlywayAutoConfiguration.class)
@ConditionalOnClass(FlywayConfigurationCustomizer.class)
public class IdentityFlywayAutoConfiguration {

    static final String IDENTITY_LOCATION = "classpath:db/identity";

    /**
     * Adds to the locations instead of replacing them: the application's own
     * migrations still have to be found.
     */
    @Bean
    FlywayConfigurationCustomizer identityMigrationsCustomizer() {
        return configuration -> {
            String[] existing = Arrays.stream(configuration.getLocations())
                    .map(Object::toString)
                    .toArray(String[]::new);
            if (Arrays.asList(existing).contains(IDENTITY_LOCATION)) {
                // The application has already added the location itself.
                return;
            }
            String[] combined = Arrays.copyOf(existing, existing.length + 1);
            combined[existing.length] = IDENTITY_LOCATION;
            configuration.locations(combined);
        };
    }
}
