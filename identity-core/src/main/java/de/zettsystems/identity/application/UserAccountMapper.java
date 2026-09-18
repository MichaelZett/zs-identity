package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.values.ScopedRole;
import de.zettsystems.identity.values.UserAccountDto;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Translates the internal entity into the view visible from the outside.
 *
 * <p>Has to be called inside a transaction: the assignments and the roles on
 * them are mapped LAZY, so outside the session there would be a
 * LazyInitializationException.
 */
final class UserAccountMapper {

    private UserAccountMapper() {
        // Utility class
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
                user.getLocale(),
                user.isClaimed());
    }
}
