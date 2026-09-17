package de.zettsystems.identity.application;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Objects;

/**
 * A thin wrapper around the {@link PasswordEncoder}.
 *
 * <p>Its purpose: in Spring Security, {@code PasswordEncoder#encode} is
 * declared so that it may return {@code null}. Without this check a possibly
 * empty hash travels all the way into the entity, and the analysis tools
 * report a possible null dereference at every call site. The contract is
 * pinned down once at the boundary instead of being repeated in four places.
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
