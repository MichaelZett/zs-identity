package de.zettsystems.identity.application;

import java.util.Locale;

/**
 * Löst die Texte des Bausteins auf — Mailtexte, Oberflächentexte und die
 * Meldungen zu {@link de.zettsystems.identity.values.IdentityMessageKeys}.
 *
 * <p>Ein Port wie {@link IdentityMailSender}: Die Voreinstellung liest die
 * mitgelieferten Sprachdateien (Deutsch und Englisch). Eine Anwendung, die
 * eigene Texte setzen oder ihren vorhandenen {@code MessageSource} bzw.
 * Vaadins {@code I18NProvider} verwenden will, stellt eine eigene Bean bereit
 * und verdrängt damit die Voreinstellung — einzelne Schlüssel überschreiben
 * und den Rest an die mitgelieferte Auflösung weiterreichen inklusive.
 */
public interface IdentityMessages {

    /**
     * Die mitgelieferte Auflösung über die Sprachdateien des Bausteins.
     *
     * <p>Für Anwendungen, die nur einzelne Schlüssel ersetzen wollen: eigene
     * Bean, die bekannte Schlüssel selbst beantwortet und alles Übrige hierhin
     * weiterreicht.
     *
     * @return eine neue, gemeinsam nutzbare Instanz
     */
    static IdentityMessages resourceBundles() {
        return new ResourceBundleIdentityMessages();
    }

    /**
     * Liefert den Text zu einem Schlüssel.
     *
     * @param key    Schlüssel aus den mitgelieferten Sprachdateien oder aus
     *               {@link de.zettsystems.identity.values.IdentityMessageKeys}
     * @param locale gewünschte Sprache; ohne passende Datei greift Englisch
     * @param args   Platzhalterwerte im Format von {@link java.text.MessageFormat}
     * @return der Text, oder der Schlüssel selbst, wenn es dazu keinen gibt
     */
    String get(String key, Locale locale, Object... args);
}
