package de.zettsystems.identity.values;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How the building block's database migrations run, prefix
 * {@code zs.identity.migrations}.
 *
 * @param enabled whether the building block migrates its own schema when the
 *                application's Flyway is set up (the default). Off means the
 *                application calls {@code IdentityMigrations#migrate} itself,
 *                at a point of its own choosing -- for an application that runs
 *                Flyway by hand, say, or that has to interleave a data copy of
 *                its own. Off is not "no migrations": without them Hibernate's
 *                schema validation fails at startup.
 */
public record MigrationSettings(@DefaultValue("true") boolean enabled) {

    public static MigrationSettings defaults() {
        return new MigrationSettings(true);
    }
}
