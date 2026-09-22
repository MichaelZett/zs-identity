package de.zettsystems.identity.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.NotificationsKt.getNotifications;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The UI rules from {@link IdentityFormView}, pinned down here so that they do
 * not live in the Javadoc alone.
 *
 * <p>The test runs across <strong>every</strong> anonymously reachable view of
 * the building block: the rules are only rules if no view leaves them out. A
 * new view therefore belongs in {@link #allViews()}. Missing there are only
 * {@code ChangePasswordView} and {@code PasskeyView}, which show nothing at all
 * without a signed-in account; their shape is checked by
 * {@code ChangePasswordViewTest} and {@code PasskeyViewTest}.
 */
class IdentityViewLayoutTest extends AbstractViewTest {

    private static final IdentityProperties PROPERTIES = IdentityProperties.defaults();

    /** Every view in the state a person first sees it in. */
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
                .as("without fixed classes an application cannot style the views along")
                .contains(IdentityFormView.VIEW_CLASS, IdentityFormView.VIEW_CLASS + "--" + name);
    }

    /**
     * The lower bound from the backlog: usable on a phone as on a desktop, no
     * horizontal scrolling at 375 px. A fixed pixel width would be exactly the
     * mistake that breaks it.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("allViews")
    void noViewCarriesAFixedPixelWidth(String name, Supplier<IdentityFormView> factory) {
        IdentityFormView view = show(factory.get());

        assertThat(widthsIn(view))
                .as("%s contains a fixed width, so the page scrolls sideways on a phone", name)
                .noneMatch(width -> width.endsWith("px"));
    }

    /**
     * The footer links are the one exception, and a deliberate one: they are
     * ways out, not actions, and making them fill the column would turn the
     * footer into the third row of full-width boxes that 0.14.0 set out to
     * remove. They are marked as such
     * ({@link IdentityFormView#FOOTER_LINK_CLASS}), so the exception is
     * visible in the component rather than hidden in this test.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("allViews")
    void everyFieldAndButtonFillsTheColumn(String name, Supplier<IdentityFormView> factory) {
        IdentityFormView view = show(factory.get());

        List<Component> interactive = descendants(view)
                .filter(child -> child instanceof EmailField || child instanceof PasswordField
                        || child instanceof Button)
                .filter(child -> !child.getElement().getClassList().contains(IdentityFormView.FOOTER_LINK_CLASS))
                .toList();

        assertThat(interactive)
                .as("%s shows neither a field nor a button, so the test checks nothing", name)
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
     * Sign-in is the exception: it takes up the whole page and puts its content
     * in the middle instead of sticking to the top edge.
     */
    @Test
    void theLoginFillsThePageInstead() {
        LoginView view = show(new LoginView(new FakeRegistrationService(), PROPERTIES, MESSAGES));

        assertThat(view.getMaxWidth()).isEqualTo("100%");
        assertThat(view.getHeight()).isEqualTo("100%");
    }

    /**
     * The one field group that does not come from us: Vaadin's login form has
     * no {@code HasSize} and sizes and pads its wrapper inside the shadow DOM,
     * so the fields were narrower than the buttons below. Found at phone width
     * in an application on 0.7.1.
     */
    @Test
    void theLoginFormLinesUpWithTheButtonsBelowIt() {
        LoginView view = show(new LoginView(new FakeRegistrationService(), PROPERTIES, MESSAGES));

        LoginForm form = _get(view, LoginForm.class);
        assertThat(form.getStyle().get("width"))
                .as("without a width the host shrinks to its content once the wrapper follows it")
                .isEqualTo("100%");
        assertThat(form.getStyle().get(LoginView.FORM_WIDTH_PROPERTY))
                .as("the wrapper keeps its own 360px unless told otherwise")
                .isEqualTo("100%");
        assertThat(form.getStyle().get(LoginView.FORM_PADDING_PROPERTY))
                .as("the wrapper's padding would inset the fields against the buttons")
                .isEqualTo("0");
    }

    /**
     * The head of the application (since 0.9.1): first element above the form,
     * full column width, created per view with the view's name -- on the
     * sign-in page inside the column, everywhere else in the view itself.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("allViews")
    void anApplicationHeadSitsFirstAboveTheForm(String name, Supplier<IdentityFormView> factory) {
        List<String> askedFor = new ArrayList<>();
        IdentityFormView view = factory.get();
        view.setHeader(viewName -> {
            askedFor.add(viewName);
            Div head = new Div("Vereinslogo");
            head.setId("app-head");
            return head;
        });
        show(view);

        Component holder = view instanceof LoginView
                ? view.getChildren().filter(VerticalLayout.class::isInstance).findFirst().orElseThrow()
                : view;
        Component first = holder.getChildren().findFirst().orElseThrow();
        assertThat(askedFor).containsExactly(name);
        assertThat(first.getId()).contains("app-head");
        assertThat(first.getClassNames()).contains(IdentityFormView.VIEW_CLASS + "__header");
        assertThat(((HasSize) first).getWidth()).isEqualTo("100%");
    }

    /** Without the bean nothing is added -- not even an empty slot. */
    @Test
    void withoutAHeadTheFormComesFirst() {
        ForgotPasswordView view = show(new ForgotPasswordView(new FakePasswordResetService(), PROPERTIES, MESSAGES));

        assertThat(view.getChildren().findFirst().orElseThrow()).isInstanceOf(H2.class);
    }

    /** The hook for an application's theme. */
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

        // Submit an empty form: the shortest route to a notification.
        _click(_get(view, Button.class, spec -> spec.withId("registration-submit-button")));

        assertThat(getNotifications())
                .singleElement()
                .extracting(Notification::getDuration)
                .isEqualTo(12_000);
    }

    /** Say what this is about before asking; that is a rule too. */
    @Test
    void aFormExplainsItselfBeforeItAsksForSomething() {
        ForgotPasswordView view = show(new ForgotPasswordView(new FakePasswordResetService(), PROPERTIES, MESSAGES));

        assertThat(view.getChildren().filter(H2.class::isInstance)).hasSize(1);
        assertThat(view.getChildren().filter(Paragraph.class::isInstance)).isNotEmpty();
    }

    private static Arguments view(String name, Supplier<IdentityFormView> factory) {
        return Arguments.of(name, factory);
    }

    /** Views that build their content only in {@code beforeEnter}. */
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
