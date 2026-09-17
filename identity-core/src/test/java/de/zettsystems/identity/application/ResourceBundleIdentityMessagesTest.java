package de.zettsystems.identity.application;

import de.zettsystems.identity.values.IdentityMessageKeys;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceBundleIdentityMessagesTest {

    private final IdentityMessages testee = IdentityMessages.resourceBundles();

    @Test
    void germanComesFromTheGermanFile() {
        assertThat(testee.get(IdentityMessageKeys.EMAIL_ALREADY_REGISTERED, Locale.GERMAN))
                .isEqualTo("Zu dieser E-Mail-Adresse gibt es bereits ein Konto.");
    }

    @Test
    void englishComesFromTheBaseFile() {
        assertThat(testee.get(IdentityMessageKeys.EMAIL_ALREADY_REGISTERED, Locale.ENGLISH))
                .isEqualTo("An account already exists for this e-mail address.");
    }

    /** Without a bundle of its own the base bundle applies, not the language of the server. */
    @Test
    void anUnknownLanguageFallsBackToTheBaseFile() {
        assertThat(testee.get(IdentityMessageKeys.ACCOUNT_NOT_FOUND, Locale.JAPANESE))
                .isEqualTo("That did not work. Please try again later.");
    }

    @Test
    void regionalVariantsUseTheirLanguageFile() {
        assertThat(testee.get(IdentityMessageKeys.TOKEN_EXPIRED, Locale.forLanguageTag("de-AT")))
                .startsWith("Dieser Link ist abgelaufen");
    }

    @Test
    void placeholdersAreFilledIn() {
        assertThat(testee.get(IdentityMessageKeys.PASSWORD_TOO_SHORT, Locale.GERMAN, 14))
                .isEqualTo("Das Passwort muss mindestens 14 Zeichen lang sein.");
    }

    @Test
    void singularAndPluralComeFromTheSamePattern() {
        assertThat(testee.get("identity.mail.validity.hours", Locale.GERMAN, 1L)).isEqualTo("eine Stunde");
        assertThat(testee.get("identity.mail.validity.hours", Locale.GERMAN, 3L)).isEqualTo("3 Stunden");
        assertThat(testee.get("identity.mail.validity.minutes", Locale.ENGLISH, 1L)).isEqualTo("one minute");
        assertThat(testee.get("identity.mail.validity.minutes", Locale.ENGLISH, 45L)).isEqualTo("45 minutes");
    }

    /**
     * No crash and no empty space: an unknown key ends up visible on screen and
     * is therefore noticed.
     */
    @Test
    void anUnknownKeyIsReturnedAsItIs() {
        assertThat(testee.get("identity.does.not.exist", Locale.GERMAN)).isEqualTo("identity.does.not.exist");
    }

    /**
     * The UI texts live in {@code identity-vaadin}. A pure REST application
     * embeds the core only, in which case that set of bundles is missing and
     * the lookup has to skip it silently rather than fail.
     */
    @Test
    void aMissingBundleIsSkipped() {
        assertThat(testee.get("identity.login.title", Locale.GERMAN))
                .as("identity-vaadin is not on the classpath here")
                .isEqualTo("identity.login.title");
    }
}
