package de.zettsystems.identity.configuration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Hängt den Migrations-Ablageort des Bausteins an die Flyway-Konfiguration der
 * Anwendung an.
 *
 * <p>Ohne das müsste jede einbindende Anwendung
 * {@code classpath:db/identity} von Hand in {@code spring.flyway.locations}
 * eintragen — und wer das vergisst, bekommt beim Start ein
 * "Schema validation: missing table [auth_role]", dessen Ursache man erst
 * suchen muss. Der Baustein bringt seine Tabellen jetzt selbst mit.
 *
 * <p>Greift nur, wenn die Anwendung Flyway überhaupt einsetzt. Der Baustein
 * selbst hat keine Flyway-Abhängigkeit (siehe {@code compileOnly} im
 * Build-Skript); wer ein anderes Migrationswerkzeug nutzt, übernimmt das SQL
 * aus {@code db/identity} eben dorthin.
 */
@AutoConfiguration(before = FlywayAutoConfiguration.class)
@ConditionalOnClass(FlywayConfigurationCustomizer.class)
public class IdentityFlywayAutoConfiguration {

    static final String IDENTITY_LOCATION = "classpath:db/identity";

    /**
     * Ergänzt die Ablageorte, statt sie zu ersetzen — die Migrationen der
     * Anwendung müssen weiterhin gefunden werden.
     */
    @Bean
    FlywayConfigurationCustomizer identityMigrationsCustomizer() {
        return configuration -> {
            String[] existing = java.util.Arrays.stream(configuration.getLocations())
                    .map(Object::toString)
                    .toArray(String[]::new);
            if (java.util.Arrays.asList(existing).contains(IDENTITY_LOCATION)) {
                // Die Anwendung hat den Ablageort bereits selbst eingetragen.
                return;
            }
            String[] combined = java.util.Arrays.copyOf(existing, existing.length + 1);
            combined[existing.length] = IDENTITY_LOCATION;
            configuration.locations(combined);
        };
    }
}
