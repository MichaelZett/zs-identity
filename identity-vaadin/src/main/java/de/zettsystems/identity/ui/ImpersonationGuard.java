package de.zettsystems.identity.ui;

import com.vaadin.flow.function.SerializableSupplier;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterListener;
import com.vaadin.flow.server.VaadinService;
import de.zettsystems.identity.application.ImpersonatedUser;
import de.zettsystems.identity.application.ImpersonationService;

import java.io.Serial;

/**
 * Checks a running impersonation on <em>every</em> navigation and ends it
 * once the account no longer allows it -- its person has redeemed the
 * invitation, say (since 1.5.0). The page is then loaded again, so that the
 * application's layout is built for the administrator, not the account acted
 * as.
 *
 * <p>Without an impersonation the guard reads the {@code SecurityContext} and
 * nothing else; only with one does it ask the database.
 *
 * <p>Attached per {@code UI} by the {@link ImpersonationServiceInitListener};
 * tests attach it themselves.
 */
public final class ImpersonationGuard implements BeforeEnterListener {

    @Serial
    private static final long serialVersionUID = 1L;

    private final SerializableSupplier<ImpersonationService> impersonationService;

    /** Looks the service up in the application context when it is needed. */
    public ImpersonationGuard() {
        this(() -> VaadinService.getCurrent().getInstantiator().getOrCreate(ImpersonationService.class));
    }

    ImpersonationGuard(SerializableSupplier<ImpersonationService> impersonationService) {
        this.impersonationService = impersonationService;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (ImpersonatedUser.current().isPresent() && !impersonationService.get().verify()) {
            event.getUI().getPage().reload();
        }
    }
}
