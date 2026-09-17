package de.zettsystems.identity.application;

import java.util.Locale;

/**
 * Resolves the texts of the building block: mail texts, UI texts and the
 * messages behind {@link de.zettsystems.identity.values.IdentityMessageKeys}.
 *
 * <p>A port like {@link IdentityMailSender}. The default reads the shipped
 * message bundles (German and English). An application that wants its own
 * texts, or wants to use its existing {@code MessageSource} or Vaadin's
 * {@code I18NProvider}, provides a bean of its own and thereby displaces the
 * default -- including overriding single keys and passing the rest on to the
 * shipped resolution.
 */
public interface IdentityMessages {

    /**
     * The shipped resolution through the message bundles of this building
     * block.
     *
     * <p>For applications that want to replace single keys only: a bean of
     * their own that answers the keys it knows and passes everything else on
     * to this one.
     *
     * @return a new, shareable instance
     */
    static IdentityMessages resourceBundles() {
        return new ResourceBundleIdentityMessages();
    }

    /**
     * Returns the text for a key.
     *
     * @param key    key from the shipped message bundles or from
     *               {@link de.zettsystems.identity.values.IdentityMessageKeys}
     * @param locale desired language; without a matching bundle English applies
     * @param args   placeholder values in the format of {@link java.text.MessageFormat}
     * @return the text, or the key itself when there is none
     */
    String get(String key, Locale locale, Object... args);
}
