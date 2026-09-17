package de.zettsystems.identity.testsupport;

import org.testcontainers.utility.DockerImageName;

/**
 * The PostgreSQL image used by the tests. The version comes from
 * {@code gradle.properties} and is passed by Gradle as a system property; the
 * fallback value only applies when running directly from the IDE.
 */
public final class PostgresTestImage {

    private static final String FALLBACK_VERSION = "17.10";

    private PostgresTestImage() {
        // Utility class
    }

    public static DockerImageName resolve() {
        String version = System.getProperty("postgres.version", FALLBACK_VERSION);
        return DockerImageName.parse("postgres:" + version + "-alpine")
                .asCompatibleSubstituteFor("postgres");
    }
}
