package de.zettsystems.identity.ui;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

import java.io.Serial;

/**
 * Attaches the {@link PasswordChangeGuard} to every new {@code UI}.
 *
 * <p>Registered through {@code META-INF/services} (the ServiceLoader) rather
 * than as a Spring bean: that way the building block needs no Vaadin
 * auto-configuration, and the rule "no {@code @ComponentScan}" stays
 * untouched. Vaadin finds the listener as soon as {@code identity-vaadin} is on
 * the classpath.
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
