package de.zettsystems.identity.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinService;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.values.IdentityProperties;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;

/**
 * Texte einer Ansicht in der Sprache der aufrufenden Person.
 *
 * <p>Die Sprache wird einmal beim Aufbau der Ansicht bestimmt — Ansichten
 * entstehen pro Aufruf neu, ein Wechsel während der Anzeige ist also kein Fall.
 *
 * <p>Maßgeblich ist die Sprache der {@code UI}, aber nur wenn die Anwendung
 * überhaupt eine Sprachwahl betreibt, also einen {@code I18NProvider}
 * mitbringt. Ohne einen solchen setzt Vaadin die UI-Sprache auf die
 * Voreinstellung der Server-JVM — die sagt nichts über die Anwendung aus und
 * unterscheidet sich zwischen Entwicklungsrechner und Container. Dann gilt
 * {@code zs.identity.locale}.
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

    /** Siehe {@link IdentityMessages#get(String, Locale, Object...)}. */
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
