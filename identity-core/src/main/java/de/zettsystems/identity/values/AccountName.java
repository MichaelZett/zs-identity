package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The name of an account.
 *
 * <p>The building block knows two shapes, selected through
 * {@link IdentityProperties#nameMode()}. Applications that use real names
 * (clubs, groups) keep a first and a last name and derive the display name
 * from them. Applications with freely chosen names (games, communities) keep
 * the display name only; first and last name stay empty.
 *
 * <p>{@code displayName} is set in both cases. It is what other people see,
 * and the only part embedding code may rely on.
 *
 * @param displayName publicly visible name, never empty
 * @param firstName   first name, {@code null} with a freely chosen display name
 * @param lastName    last name, {@code null} with a freely chosen display name
 */
public record AccountName(String displayName, @Nullable String firstName, @Nullable String lastName) {

    public AccountName {
        displayName = requireNonBlank(displayName, "displayName");
        firstName = blankToNull(firstName);
        lastName = blankToNull(lastName);
    }

    /** Real name: the display name is "first name last name". */
    public static AccountName of(String firstName, String lastName) {
        String first = requireNonBlank(firstName, "firstName");
        String last = requireNonBlank(lastName, "lastName");
        return new AccountName(first + " " + last, first, last);
    }

    /** A freely chosen display name, without a first and last name. */
    public static AccountName display(String displayName) {
        return new AccountName(displayName, null, null);
    }

    /** Whether first and last name are known. */
    public boolean hasFullName() {
        return firstName != null && lastName != null;
    }

    private static String requireNonBlank(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
