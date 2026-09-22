package de.zettsystems.identity.ui;

import com.github.mvysny.kaributesting.v10.KaribuConfig;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.internal.PendingJavaScriptInvocation;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.internal.JacksonUtils;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.values.IdentityProperties;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.github.mvysny.kaributesting.v10.NotificationsKt.getNotifications;
import static com.github.mvysny.kaributools.NotificationsKt.getText;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The shared frame of the view tests: Karibu recreates {@code VaadinSession}
 * and {@code UI} in memory, so that the views can be checked without a servlet
 * container and without a browser.
 *
 * <p>Only empty target views are registered under the paths of the building
 * block. The real views need constructor arguments and could therefore not be
 * created by the router at all -- the tests build them themselves and attach
 * them to the UI. The targets are needed nonetheless: without them every
 * navigation button fails on an unknown path.
 */
abstract class AbstractViewTest {

    @Route(value = IdentityRoutes.LOGIN, autoLayout = false)
    public static class LoginTarget extends Div {
    }

    @Route(value = IdentityRoutes.REGISTER, autoLayout = false)
    public static class RegisterTarget extends Div {
    }

    @Route(value = IdentityRoutes.RESEND_VERIFICATION, autoLayout = false)
    public static class ResendTarget extends Div {
    }

    @Route(value = IdentityRoutes.FORGOT_PASSWORD, autoLayout = false)
    public static class ForgotPasswordTarget extends Div {
    }

    /** The root of the application, the target after a password change. */
    @Route(value = "", autoLayout = false)
    public static class RootTarget extends Div {
    }

    /** Some view of the application; the guard is meant to block it. */
    @Route(value = "somewhere", autoLayout = false)
    public static class SomewhereTarget extends Div {
    }

    @Route(value = IdentityRoutes.CHANGE_PASSWORD, autoLayout = false)
    public static class ChangePasswordTarget extends Div {
    }

    /** The shipped resolution: the tests check the real texts, not stand-ins. */
    protected static final IdentityMessages MESSAGES = IdentityMessages.resourceBundles();

    /**
     * What the views asked the browser to run (the passkey ceremonies). Karibu
     * hands every pending invocation to this hook on a client round trip; a
     * test plays the browser's answer back through
     * {@link PendingJavaScriptInvocation#complete} or
     * {@link PendingJavaScriptInvocation#completeExceptionally}.
     */
    private final List<PendingJavaScriptInvocation> browserCalls = new ArrayList<>();
    private final Function1<PendingJavaScriptInvocation, Unit> captureBrowserCalls = invocation -> {
        browserCalls.add(invocation);
        return Unit.INSTANCE;
    };

    @BeforeEach
    void setUpVaadin() {
        Routes routes = new Routes();
        routes.getRoutes().addAll(List.of(LoginTarget.class, RegisterTarget.class,
                ResendTarget.class, ForgotPasswordTarget.class, RootTarget.class,
                SomewhereTarget.class, ChangePasswordTarget.class));
        MockVaadin.setup(routes);
        KaribuConfig.getPendingJavascriptInvocationHandlers().add(captureBrowserCalls);
    }

    @AfterEach
    void tearDownVaadin() {
        KaribuConfig.getPendingJavascriptInvocationHandlers().remove(captureBrowserCalls);
        MockVaadin.tearDown();
    }

    /**
     * Flushes what was queued for the browser and returns every script whose
     * source contains the marker, oldest first. Vaadin queues scripts of its
     * own (titles, focus), so the views' are picked out by content.
     */
    protected final List<PendingJavaScriptInvocation> browserCallsContaining(String marker) {
        MockVaadin.clientRoundtrip();
        return browserCalls.stream()
                .filter(call -> call.getInvocation().getExpression().contains(marker))
                .toList();
    }

    /** The same, where exactly one is expected. */
    protected final PendingJavaScriptInvocation browserCall(String marker) {
        List<PendingJavaScriptInvocation> matching = browserCallsContaining(marker);
        if (matching.size() != 1) {
            throw new AssertionError("Expected exactly one script containing '" + marker + "', found "
                    + matching.size());
        }
        return matching.getFirst();
    }

    /** The browser's answer to a script: what its promise resolved with. */
    protected static void browserResolves(PendingJavaScriptInvocation call, String value) {
        call.complete(JacksonUtils.createNode(value));
    }

    /** The browser's answer to a script: what its promise rejected with. */
    protected static void browserRejects(PendingJavaScriptInvocation call, String value) {
        call.completeExceptionally(JacksonUtils.createNode(value));
    }

    /** Attaches the view to the UI; only then do Karibu's lookups find it. */
    protected static <T extends Component> T show(T view) {
        UI.getCurrent().add(view);
        return view;
    }

    protected static IdentityProperties properties(IdentityProperties base, Locale locale) {
        return base.withLocale(locale);
    }

    /**
     * {@code BeforeEnterEvent} cannot sensibly be built by hand: the real
     * constructor demands a router together with a navigation target. Only the
     * address is needed anyway.
     */
    protected static BeforeEnterEvent enterEventWith(String path, Map<String, List<String>> parameters) {
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getLocation()).thenReturn(new Location(path, new QueryParameters(parameters)));
        return event;
    }

    protected static BeforeEnterEvent enterEventWithToken(String path, String token) {
        return enterEventWith(path, Map.of(IdentityRoutes.TOKEN_PARAMETER, List.of(token)));
    }

    protected static List<String> notificationTexts() {
        return getNotifications().stream()
                .map(AbstractViewTest::textOf)
                .toList();
    }

    private static String textOf(Notification notification) {
        return getText(notification);
    }

    protected static String currentPath() {
        return UI.getCurrent().getInternals().getActiveViewLocation().getPath();
    }
}
