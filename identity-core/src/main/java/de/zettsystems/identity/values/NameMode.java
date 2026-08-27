package de.zettsystems.identity.values;

/**
 * Welche Namensangaben eine Anwendung von neuen Konten verlangt. Wirkt auf
 * das Registrierungsformular; im Kern sind beide Gestalten immer zulässig
 * (siehe {@link AccountName}).
 */
public enum NameMode {

    /** Vor- und Nachname sind Pflicht; der Anzeigename wird daraus gebildet. */
    FULL_NAME,

    /** Nur ein frei gewählter Anzeigename (Spielername, Nickname). */
    DISPLAY_NAME
}
