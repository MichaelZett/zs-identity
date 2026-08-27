package de.zettsystems.identity.application;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Objects;

/**
 * Dünne Hülle um den {@link PasswordEncoder}.
 *
 * <p>Zweck: {@code PasswordEncoder#encode} ist in Spring Security so deklariert,
 * dass es {@code null} liefern darf. Ohne diese Prüfung wandert ein
 * möglicherweise leerer Hash bis in die Entity, und die Analysewerkzeuge melden
 * an jeder Aufrufstelle einen möglichen Nullzugriff. Hier wird der Vertrag
 * einmal an der Grenze festgezurrt, statt ihn an vier Stellen zu wiederholen.
 */
class PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    PasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    String hash(String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword");
        return Objects.requireNonNull(passwordEncoder.encode(rawPassword),
                "PasswordEncoder returned no hash");
    }
}
