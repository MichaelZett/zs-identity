package de.zettsystems.identity.values;

/**
 * The database schema the building block keeps its tables in.
 *
 * <p>Since 0.8.0 the tables live in a schema of their own instead of the
 * application's default schema, together with their own Flyway history
 * ({@code identity.flyway_schema_history}). That is what lets the building
 * block and the application grow independently: neither has to reserve a
 * version space in the other's history, and an application may draw a
 * baseline over its own migrations without swallowing ours.
 *
 * <p>The name is fixed rather than configurable: JPA annotations need a
 * compile-time constant, and an application that references
 * {@code identity.auth_user} from its own migrations needs a name that does
 * not move.
 */
public final class IdentitySchema {

    /** The schema name, for {@code @Table(schema = ...)} and for SQL in applications. */
    public static final String NAME = "identity";

    /** The table an application may reference for its foreign keys. */
    public static final String USER_TABLE = NAME + ".auth_user";

    private IdentitySchema() {
        // constants only
    }
}
