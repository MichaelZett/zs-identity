package de.zettsystems.identity.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterObserver;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.UiSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.NotificationsKt.getNotifications;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Oberflächen-Regeln aus {@link IdentityFormView} — hier festgeschrieben,
 * damit sie nicht nur im Javadoc stehen.
 *
 * <p>Der Test läuft über <strong>alle</strong> anonym erreichbaren Ansichten
 * des Bausteins: Die Regeln sind nur dann welche, wenn keine Ansicht sie
 * auslässt. Eine neue Ansicht gehört deshalb in {@link #allViews()}. Fehlt
 * dort nur die {@code ChangePasswordView} — sie zeigt ohne angemeldetes Konto
 * gar nichts an, und ihre Gestalt prüft der {@code ChangePasswordViewTest}.
 */
class IdentityViewLayoutTest extends AbstractViewTest {

    private static final IdentityProperties PROPERTIES = IdentityProperties.defaults();

    /** Jede Ansicht in dem Zustand, in dem eine Person sie zuerst sieht. */
    static Stream<Arguments> allViews() {
        return Stream.of(
                view("login", () -> new LoginView(new FakeRegistrationService(), PROPERTIES, MESSAGES)),
                view("registration", () -> new RegistrationView(new FakeRegistrationService(), PROPERTIES, MESSAGES)),
                view("forgot-password",
                        () -> new ForgotPasswordView(new FakePasswordResetService(), PROPERTIES, MESSAGES)),
                view("resend-verification",
                        () -> new ResendVerificationView(new FakeRegistrationService(), PROPERTIES, MESSAGES)),
                view("reset-password", () -> entered(
                        new ResetPasswordView(new FakePasswordResetService(), PROPERTIES, MESSAGES),
                        IdentityRoutes.RESET_PASSWORD)),
                view("claim-account", () -> entered(
                        new ClaimAccountView(new FakeInvitationService(), PROPERTIES, MESSAGES),
                        IdentityRoutes.CLAIM_ACCOUNT)),
                view("confirm-email", () -> entered(
                        new ConfirmEmailView(new FakeRegistrationService(), PROPERTIES, MESSAGES),
                        IdentityRoutes.CONFIRM_EMAIL)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allViews")
    void everyViewCarriesItsCssHooks(String name, Supplier<IdentityFormView> factory) {
        IdentityFormView view = show(factory.get());

        assertThat(view.getClassNames())
                .as("ohne feste Klassen kann eine Anwendung die Ansichten nicht mitstylen")
                .contains(IdentityFormView.VIEW_CLASS, IdentityFormView.VIEW_CLASS + "--" + name);
    }

    /**
     * Die Untergrenze aus dem Backlog: brauchbar am Telefon wie am Rechner,
     * kein Querscrollen bei 375 px. Eine feste Pixelbreite wäre genau der
     * Fehler, der das bricht.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("allViews")
    void noViewCarriesAFixedPixelWidth(String name, Supplier<IdentityFormView> factory) {
        IdentityFormView view = show(factory.get());

        assertThat(widthsIn(view))
                .as("%s enthält eine feste Breite — am Telefon scrollt die Seite dann quer", name)
                .noneMatch(width -> width.endsWith("px"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allViews")
    void everyFieldAndButtonFillsTheColumn(String name, Supplier<IdentityFormView> factory) {
        IdentityFormView view = show(factory.get());

        List<Component> interactive = descendants(view)
                .filter(child -> child instanceof EmailField || child instanceof PasswordField
                        || child instanceof Button)
                .toList();

        assertThat(interactive)
                .as("%s zeigt weder Feld noch Knopf — dann prüft der Test nichts", name)
                .isNotEmpty()
                .allSatisfy(component -> assertThat(((HasSize) component).getWidth()).isEqualTo("100%"));
    }

    @Test
    void theColumnStopsGrowingAtTheConfiguredWidth() {
        RegistrationView view = show(new RegistrationView(new FakeRegistrationService(),
                PROPERTIES.withUi(new UiSettings("40rem", List.of(), Duration.ofSeconds(5))), MESSAGES));

        assertThat(view.getMaxWidth()).isEqualTo("40rem");
        assertThat(view.getWidth()).isEqualTo("100%");
    }

    /**
     * Die Anmeldung ist die Ausnahme: Sie nimmt die ganze Seite ein und setzt
     * ihren Inhalt in die Mitte, statt oben am Rand zu kleben.
     */
    @Test
    void theLoginFillsThePageInstead() {
        LoginView view = show(new LoginView(new FakeRegistrationService(), PROPERTIES, MESSAGES));

        assertThat(view.getMaxWidth()).isEqualTo("100%");
        assertThat(view.getHeight()).isEqualTo("100%");
    }

    /** Der Andockpunkt für das Theme einer Anwendung. */
    @Test
    void theApplicationCanHangItsOwnClassesOnEveryView() {
        IdentityProperties styled = PROPERTIES.withUi(
                new UiSettings("28rem", List.of("my-app-card", "my-app-elevated"), Duration.ofSeconds(5)));

        ForgotPasswordView view = show(new ForgotPasswordView(new FakePasswordResetService(), styled, MESSAGES));

        assertThat(view.getClassNames()).contains("my-app-card", "my-app-elevated");
    }

    @Test
    void theHintDurationFollowsTheConfiguration() {
        IdentityProperties patient = PROPERTIES.withUi(
                new UiSettings("28rem", List.of(), Duration.ofSeconds(12)));
        RegistrationView view = show(new RegistrationView(new FakeRegistrationService(), patient, MESSAGES));

        // Leeres Formular abschicken: Das ist der kürzeste Weg zu einem Hinweis.
        _click(_get(view, Button.class, spec -> spec.withId("registration-submit-button")));

        assertThat(getNotifications())
                .singleElement()
                .extracting(Notification::getDuration)
                .isEqualTo(12_000);
    }

    /** Erst sagen, worum es geht, dann fragen — auch das ist eine Regel. */
    @Test
    void aFormExplainsItselfBeforeItAsksForSomething() {
        ForgotPasswordView view = show(new ForgotPasswordView(new FakePasswordResetService(), PROPERTIES, MESSAGES));

        assertThat(view.getChildren().filter(H2.class::isInstance)).hasSize(1);
        assertThat(view.getChildren().filter(Paragraph.class::isInstance)).isNotEmpty();
    }

    private static Arguments view(String name, Supplier<IdentityFormView> factory) {
        return Arguments.of(name, factory);
    }

    /** Ansichten, die ihren Inhalt erst in {@code beforeEnter} aufbauen. */
    private static <V extends IdentityFormView & BeforeEnterObserver> IdentityFormView entered(V view, String route) {
        view.beforeEnter(enterEventWithToken(route, "ein-token"));
        return view;
    }

    private static Stream<Component> descendants(Component component) {
        return component.getChildren()
                .flatMap(child -> Stream.concat(Stream.of(child), descendants(child)));
    }

    private static Stream<String> widthsIn(Component component) {
        return component.getChildren()
                .flatMap(child -> Stream.concat(
                        child instanceof HasSize sized && sized.getWidth() != null
                                ? Stream.of(sized.getWidth())
                                : Stream.empty(),
                        widthsIn(child)));
    }
}
