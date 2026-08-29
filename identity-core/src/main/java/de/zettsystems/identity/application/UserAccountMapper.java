package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.Role;
import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.values.UserAccountDto;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Übersetzt die interne Entity in die nach außen sichtbare Sicht.
 *
 * <p>Muss innerhalb einer Transaktion aufgerufen werden: {@code roles} ist LAZY
 * gemappt, außerhalb der Sitzung gäbe es eine LazyInitializationException.
 */
final class UserAccountMapper {

    private UserAccountMapper() {
        // Hilfsklasse
    }

    static UserAccountDto toDto(UserAccount user) {
        Set<String> roleCodes = user.getRoles().stream()
                .map(Role::getCode)
                .collect(Collectors.toUnmodifiableSet());
        return new UserAccountDto(
                Objects.requireNonNull(user.getId(), "user.id"),
                user.getEmail(),
                user.getName(),
                user.isEnabled(),
                user.isEmailVerified(),
                user.getCreatedAt(),
                roleCodes,
                user.isMustChangePassword());
    }
}
