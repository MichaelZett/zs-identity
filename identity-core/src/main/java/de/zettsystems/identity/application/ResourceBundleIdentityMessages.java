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
 * The default text resolution through the shipped message bundles.
 *
 * <p>Two sets of files, because two artifacts contribute texts: {@code core}
 * lives in {@code identity-core} (mails, error messages), {@code ui} in
 * {@code identity-vaadin} (the UI). A REST application embeds the core only,
 * in which case the second set is simply absent and the lookup skips it.
 */
class ResourceBundleIdentityMessages implements IdentityMessages {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceBundleIdentityMessages.class);

    private static final List<String> BASE_NAMES = List.of(
            "de.zettsystems.identity.messages.core",
            "de.zettsystems.identity.messages.ui");

    /**
     * Without a fallback to {@code Locale.getDefault()}: otherwise the language
     * setting of the server would have a say in which text a browser receives.
     * Only the requested language is looked up, then the base bundle (English).
     */
    private static final ResourceBundle.Control CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    /** {@code getBundle} throws when a bundle is missing, so remember what was found per language. */
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
        // Pass through untouched when there are no placeholder values:
        // MessageFormat would otherwise read single quotes as escaping.
        return args.length == 0 ? text : new MessageFormat(text, locale).format(args);
    }
}
