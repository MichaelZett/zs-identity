package de.zettsystems.identity.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.BeforeEnterEvent;
import de.zettsystems.identity.application.ImpersonatedUsers;
import de.zettsystems.identity.application.ImpersonationService;
import de.zettsystems.identity.values.Impersonation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * An impersonation whose account has been claimed in the meantime ends at the
 * next navigation, and the page loads again for the administrator.
 */
class ImpersonationGuardTest extends AbstractViewTest {

    private final AtomicInteger checks = new AtomicInteger();
    private boolean stillAllowed = true;

    private final ImpersonationService service = new ImpersonationService() {
        @Override
        public Impersonation start(Long targetUserId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean stop() {
            return false;
        }

        @Override
        public Optional<Impersonation> current() {
            return Optional.empty();
        }

        @Override
        public boolean verify() {
            checks.incrementAndGet();
            if (!stillAllowed) {
                // What the real service does: the session is the administrator's again.
                SecurityContextHolder.clearContext();
            }
            return stillAllowed;
        }
    };

    private final ImpersonationGuard guard = new ImpersonationGuard(() -> service);

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static BeforeEnterEvent navigation() {
        BeforeEnterEvent event = enterEventWith("somewhere", Map.of());
        when(event.getUI()).thenReturn(UI.getCurrent());
        return event;
    }

    @Test
    void withoutAnImpersonationNothingIsAsked() {
        UI before = UI.getCurrent();

        guard.beforeEnter(navigation());

        assertThat(checks).hasValue(0);
        assertThat(UI.getCurrent()).isSameAs(before);
    }

    @Test
    void anImpersonationThatIsStillAllowedGoesOn() {
        SecurityContextHolder.getContext().setAuthentication(ImpersonatedUsers.session(5L));
        UI before = UI.getCurrent();

        guard.beforeEnter(navigation());

        assertThat(checks).hasValue(1);
        assertThat(UI.getCurrent()).isSameAs(before);
    }

    @Test
    void anEndedImpersonationReloadsThePageForTheAdministrator() {
        SecurityContextHolder.getContext().setAuthentication(ImpersonatedUsers.session(5L));
        stillAllowed = false;
        UI before = UI.getCurrent();

        guard.beforeEnter(navigation());

        assertThat(checks).hasValue(1);
        assertThat(UI.getCurrent()).as("the page was loaded again").isNotSameAs(before);
    }
}
