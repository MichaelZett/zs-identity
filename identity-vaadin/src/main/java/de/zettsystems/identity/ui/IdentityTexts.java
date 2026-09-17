package de.zettsystems.identity.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinService;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.values.IdentityProperties;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;

/**
 * The texts of a view in the language of the person calling it.
 *
 * <p>The language is determined once while the view is being built: views are
 * created anew per navigation, so switching while one is on screen is not a
 * case that occurs.
 *
 * <p>What counts is the language of the {@code UI}, but only when the
 * application runs a language selection at all, that is when it brings an
 * {@code I18NProvider}. Without one, Vaadin sets the UI language to the default
 * of the server JVM, which says nothing about the application and differs
 * between a development machine and a container. In that case
 * {@code zs.identity.locale} applies.
 */
final class IdentityTexts implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final IdentityMessages messages;
    private final Locale locale;

    IdentityTexts(IdentityMessages messages, IdentityProperties properties) {
        this.messages = messages;
        this.locale = resolveLocale(properties.locale());
    }

    /** See {@link IdentityMessages#get(String, Locale, Object...)}. */
    String get(String key, Object... args) {
        return messages.get(key, locale, args);
    }

    Locale locale() {
        return locale;
    }

    private static Locale resolveLocale(Locale fallback) {
        VaadinService service = VaadinService.getCurrent();
        UI ui = UI.getCurrent();
        if (service == null || ui == null || service.getInstantiator().getI18NProvider() == null) {
            return fallback;
        }
        return ui.getLocale();
    }
}
