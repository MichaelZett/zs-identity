package de.zettsystems.identity.testsupport;

import org.testcontainers.utility.DockerImageName;

/**
 * Das PostgreSQL-Abbild der Tests. Die Version kommt aus
 * {@code gradle.properties} und wird von Gradle als Systemeigenschaft gesetzt;
 * der Rückfallwert greift nur bei direkten Läufen aus der IDE.
 */
public final class PostgresTestImage {

    private static final String FALLBACK_VERSION = "17.10";

    private PostgresTestImage() {
        // Hilfsklasse
    }

    public static DockerImageName resolve() {
        String version = System.getProperty("postgres.version", FALLBACK_VERSION);
        return DockerImageName.parse("postgres:" + version + "-alpine")
                .asCompatibleSubstituteFor("postgres");
    }
}
