package de.zettsystems.identity.ui;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

import java.io.Serial;

/**
 * Hängt den {@link PasswordChangeGuard} an jede neue {@code UI}.
 *
 * <p>Registriert über {@code META-INF/services} (ServiceLoader), nicht als
 * Spring-Bean: So braucht der Baustein keine Vaadin-Auto-Konfiguration, und
 * die Regel „kein {@code @ComponentScan}" bleibt unberührt. Vaadin findet den
 * Listener, sobald {@code identity-vaadin} auf dem Klassenpfad liegt.
 */
public final class PasswordChangeServiceInitListener implements VaadinServiceInitListener {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiEvent ->
                uiEvent.getUI().addBeforeEnterListener(new PasswordChangeGuard()));
    }
}
