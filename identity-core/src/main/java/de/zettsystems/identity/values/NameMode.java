package de.zettsystems.identity.values;

/**
 * Which name an application requires from new accounts. This affects the
 * registration form only; the core always accepts both shapes (see
 * {@link AccountName}).
 */
public enum NameMode {

    /** First and last name are mandatory; the display name is derived from them. */
    FULL_NAME,

    /** A freely chosen display name only (player name, nickname). */
    DISPLAY_NAME
}
