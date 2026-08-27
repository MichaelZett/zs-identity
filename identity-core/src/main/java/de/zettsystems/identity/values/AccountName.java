package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Der Name eines Kontos.
 *
 * <p>Der Baustein kennt zwei Gestalten, gesteuert über
 * {@link IdentityProperties#nameMode()}: Anwendungen mit Klarnamen (Vereine,
 * Gruppen) führen Vor- und Nachname, der Anzeigename ist daraus abgeleitet.
 * Anwendungen mit frei gewähltem Namen (Spiele, Communities) führen nur den
 * Anzeigenamen; Vor- und Nachname bleiben leer.
 *
 * <p>{@code displayName} ist in beiden Fällen gesetzt — er ist das, was
 * andere Personen sehen, und das einzige, worauf sich einbindender Code
 * verlassen darf.
 *
 * @param displayName öffentlich sichtbarer Name, nie leer
 * @param firstName   Vorname, {@code null} bei frei gewähltem Anzeigenamen
 * @param lastName    Nachname, {@code null} bei frei gewähltem Anzeigenamen
 */
public record AccountName(String displayName, @Nullable String firstName, @Nullable String lastName) {

    public AccountName {
        displayName = requireNonBlank(displayName, "displayName");
        firstName = blankToNull(firstName);
        lastName = blankToNull(lastName);
    }

    /** Klarname: Anzeigename ist „Vorname Nachname". */
    public static AccountName of(String firstName, String lastName) {
        String first = requireNonBlank(firstName, "firstName");
        String last = requireNonBlank(lastName, "lastName");
        return new AccountName(first + " " + last, first, last);
    }

    /** Frei gewählter Anzeigename ohne Vor- und Nachname. */
    public static AccountName display(String displayName) {
        return new AccountName(displayName, null, null);
    }

    /** Ob Vor- und Nachname bekannt sind. */
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
