package de.zettsystems.identity.ui;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

import java.io.Serial;

/**
 * Attaches the {@link ImpersonationGuard} to every new {@code UI} (since
 * 1.5.0). Registered through {@code META-INF/services}, for the reasons given
 * at {@link PasswordChangeServiceInitListener}.
 */
public final class ImpersonationServiceInitListener implements VaadinServiceInitListener {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiEvent ->
                uiEvent.getUI().addBeforeEnterListener(new ImpersonationGuard()));
    }
}
