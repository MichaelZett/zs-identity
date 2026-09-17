package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.values.ScopedRole;
import de.zettsystems.identity.values.UserAccountDto;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Übersetzt die interne Entity in die nach außen sichtbare Sicht.
 *
 * <p>Muss innerhalb einer Transaktion aufgerufen werden: Die Zuweisungen und
 * die Rollen daran sind LAZY gemappt, außerhalb der Sitzung gäbe es eine
 * LazyInitializationException.
 */
final class UserAccountMapper {

    private UserAccountMapper() {
        // Hilfsklasse
    }

    static UserAccountDto toDto(UserAccount user) {
        Set<ScopedRole> roleAssignments = user.getRoleAssignments().stream()
                .map(assignment -> new ScopedRole(assignment.getRole().getCode(), assignment.getScope()))
                .collect(Collectors.toUnmodifiableSet());
        return new UserAccountDto(
                Objects.requireNonNull(user.getId(), "user.id"),
                user.getEmail(),
                user.getName(),
                user.isEnabled(),
                user.isEmailVerified(),
                user.getCreatedAt(),
                roleAssignments,
                user.isMustChangePassword(),
                user.getLocale());
    }
}
