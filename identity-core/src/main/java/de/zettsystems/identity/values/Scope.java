package de.zettsystems.identity.values;

import java.io.Serializable;
import java.util.Objects;

/**
 * The scope of a role assignment: "admin <strong>of club 17</strong>" rather
 * than just "admin".
 *
 * <p>The building block never <strong>interprets</strong> it. It stores it,
 * hands it back and compares it for equality; what a {@code "club"} is, only
 * the application knows. That keeps the first rule of this building block
 * intact: no knowledge about the domain of whoever embeds it.
 *
 * <p>Two parts rather than a single key, because applications keep several
 * kinds side by side (a portal has games <em>and</em> clubs). Only that makes
 * "all clubs of this person" answerable without taking strings apart.
 *
 * <p><strong>No assignment without a scope:</strong> a <em>global</em> role is
 * not represented by a special {@code Scope} but by its absence
 * ({@code null}). An "empty scope" as a value would be a second way of saying
 * the same thing.
 *
 * <p>{@link Serializable}, because the active scope lives in the security
 * principal and therefore in the HTTP session.
 *
 * @param type kind of scope, for example {@code club} or {@code game}
 * @param id   identifier within that kind, for example {@code 17}
 */
public record Scope(String type, String id) implements Serializable {

    /**
     * Separates kind and identifier in the text form and in the authority name
     * ({@code ROLE_ADMIN@club:17}).
     */
    public static final char SEPARATOR = ':';

    /** Separates role and scope in the authority name. */
    public static final char AUTHORITY_SEPARATOR = '@';

    public Scope {
        type = requireUsable(type, "type");
        id = requireUsable(id, "id");
    }

    public static Scope of(String type, String id) {
        return new Scope(type, id);
    }

    /** {@code club:17}: the same form that appears in the authority name. */
    @Override
    public String toString() {
        return type + SEPARATOR + id;
    }

    /**
     * Reads the text form back.
     *
     * @throws IllegalArgumentException if it contains no {@value #SEPARATOR}
     */
    public static Scope parse(String text) {
        Objects.requireNonNull(text, "text");
        int separator = text.indexOf(SEPARATOR);
        if (separator < 0) {
            throw new IllegalArgumentException("Not a scope: " + text);
        }
        return new Scope(text.substring(0, separator), text.substring(separator + 1));
    }

    /**
     * Validates what will later end up inside an authority name.
     *
     * <p><strong>A security question, not a matter of taste:</strong> role and
     * scope are combined into {@code ROLE_ADMIN@club:17}. If an identifier
     * were allowed to contain an {@code @} or a {@value #SEPARATOR} itself,
     * one could invent a permission nobody granted -- the identifier
     * {@code 4@club}, say, for a club that belongs to someone else. Whitespace
     * is out for the same reason: expressions such as
     * {@code hasAuthority(..)} are split on it.
     */
    private static String requireUsable(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Scope " + field + " must not be blank");
        }
        if (normalized.indexOf(SEPARATOR) >= 0 || normalized.indexOf(AUTHORITY_SEPARATOR) >= 0
                || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(
                    "Scope " + field + " must not contain '" + SEPARATOR + "', '" + AUTHORITY_SEPARATOR
                            + "' or whitespace, was: " + value);
        }
        return normalized;
    }
}
