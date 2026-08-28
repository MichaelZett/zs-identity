package de.zettsystems.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Voreingestellte Textauflösung über die mitgelieferten Sprachdateien.
 *
 * <p>Zwei Dateisätze, weil zwei Artefakte Texte beisteuern: {@code core} liegt
 * in {@code identity-core} (Mails, Fehlermeldungen), {@code ui} in
 * {@code identity-vaadin} (Oberfläche). Eine REST-Anwendung bindet nur den Kern
 * ein — dann fehlt der zweite Satz schlicht, und die Suche überspringt ihn.
 */
class ResourceBundleIdentityMessages implements IdentityMessages {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceBundleIdentityMessages.class);

    private static final List<String> BASE_NAMES = List.of(
            "de.zettsystems.identity.messages.core",
            "de.zettsystems.identity.messages.ui");

    /**
     * Ohne Rückfall auf {@code Locale.getDefault()}: Sonst entscheidet die
     * Spracheinstellung des Servers mit, welchen Text ein Browser bekommt.
     * Gesucht wird also nur die angefragte Sprache, danach die Basisdatei
     * (Englisch).
     */
    private static final ResourceBundle.Control CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    /** {@code getBundle} wirft bei fehlender Datei — die Fundlage einmal pro Sprache merken. */
    private final Map<Locale, List<ResourceBundle>> bundlesByLocale = new ConcurrentHashMap<>();

    @Override
    public String get(String key, Locale locale, Object... args) {
        for (ResourceBundle bundle : bundlesFor(locale)) {
            if (bundle.containsKey(key)) {
                return format(bundle.getString(key), locale, args);
            }
        }
        LOG.warn("No text for identity message key '{}' ({})", key, locale);
        return key;
    }

    private List<ResourceBundle> bundlesFor(Locale locale) {
        return bundlesByLocale.computeIfAbsent(locale, requested -> BASE_NAMES.stream()
                .map(baseName -> load(baseName, requested))
                .flatMap(Optional::stream)
                .toList());
    }

    private Optional<ResourceBundle> load(String baseName, Locale locale) {
        try {
            return Optional.of(ResourceBundle.getBundle(baseName, locale,
                    ResourceBundleIdentityMessages.class.getClassLoader(), CONTROL));
        } catch (MissingResourceException e) {
            LOG.debug("No message bundle '{}' on the classpath", baseName, e);
            return Optional.empty();
        }
    }

    private static String format(String text, Locale locale, Object... args) {
        // Ohne Platzhalterwerte roh durchreichen: MessageFormat wuerde sonst
        // einfache Anfuehrungszeichen im Text als Maskierung deuten.
        return args.length == 0 ? text : new MessageFormat(text, locale).format(args);
    }
}
