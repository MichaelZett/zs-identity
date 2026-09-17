package de.zettsystems.identity.ui;

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.values.IdentityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.github.mvysny.kaributesting.v10.NotificationsKt.getNotifications;
import static com.github.mvysny.kaributools.NotificationsKt.getText;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Gemeinsamer Rahmen der Ansichtstests: Karibu baut {@code VaadinSession} und
 * {@code UI} im Speicher nach, sodass die Views ohne Servlet-Container und ohne
 * Browser prüfbar sind.
 *
 * <p>Registriert werden nur leere Ziel-Ansichten unter den Pfaden des
 * Bausteins. Die echten Views brauchen Konstruktorargumente, ließen sich also
 * gar nicht vom Router erzeugen — die Tests bauen sie selbst und hängen sie an
 * die UI. Die Ziele sind trotzdem nötig: Ohne sie scheitert jeder
 * Navigations-Knopf an einem unbekannten Pfad.
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

    /** Die Wurzel der Anwendung — Ziel nach dem Passwortwechsel. */
    @Route(value = "", autoLayout = false)
    public static class RootTarget extends Div {
    }

    /** Irgendeine Fachansicht — der Wächter soll sie versperren. */
    @Route(value = "somewhere", autoLayout = false)
    public static class SomewhereTarget extends Div {
    }

    @Route(value = IdentityRoutes.CHANGE_PASSWORD, autoLayout = false)
    public static class ChangePasswordTarget extends Div {
    }

    /** Die mitgelieferte Auflösung — die Tests prüfen die echten Texte, keine Attrappen. */
    protected static final IdentityMessages MESSAGES = IdentityMessages.resourceBundles();

    @BeforeEach
    void setUpVaadin() {
        Routes routes = new Routes();
        routes.getRoutes().addAll(List.of(LoginTarget.class, RegisterTarget.class,
                ResendTarget.class, ForgotPasswordTarget.class, RootTarget.class,
                SomewhereTarget.class, ChangePasswordTarget.class));
        MockVaadin.setup(routes);
    }

    @AfterEach
    void tearDownVaadin() {
        MockVaadin.tearDown();
    }

    /** Hängt die Ansicht an die UI — erst dann finden Karibus Suchfunktionen sie. */
    protected static <T extends Component> T show(T view) {
        UI.getCurrent().add(view);
        return view;
    }

    protected static IdentityProperties properties(IdentityProperties base, Locale locale) {
        return base.withLocale(locale);
    }

    /**
     * {@code BeforeEnterEvent} lässt sich nicht sinnvoll von Hand bauen — der
     * echte Konstruktor verlangt einen Router samt Navigationsziel. Gebraucht
     * wird ohnehin nur die Adresse.
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
