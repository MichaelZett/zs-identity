package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.internal.PendingJavaScriptInvocation;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import de.zettsystems.identity.application.ExternalProviders;
import de.zettsystems.identity.values.ExternalProvider;
import de.zettsystems.identity.values.IdentityPaths;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.router.BeforeEnterEvent;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.PasskeySettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;


import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.assertj.core.api.Assertions.assertThat;

class LoginViewTest extends AbstractViewTest {

    private final FakeRegistrationService registrationService = new FakeRegistrationService();

    private static final IdentityProperties WITH_PASSKEYS = IdentityProperties.defaults()
            .withPasskeys(PasskeySettings.defaults().enabled(true));

    private LoginView showLoginView(IdentityProperties properties) {
        return show(new LoginView(registrationService, properties, MESSAGES));
    }

    /**
     * The queued ceremony of one kind. Both run the same script, so they are
     * told apart by the mode in {@code $3}: empty for the button, "conditional"
     * for the offer in the username field.
     */
    private PendingJavaScriptInvocation passkeyCall(String mode) {
        List<PendingJavaScriptInvocation> matching = browserCallsContaining("navigator.credentials.get").stream()
                .filter(call -> mode.equals(call.getInvocation().getParameters().get(3)))
                .toList();
        assertThat(matching).as("ceremonies in mode '%s'", mode).hasSize(1);
        return matching.getFirst();
    }

    @Test
    void theFormIsLabelledInTheConfiguredLanguage() {
        LoginView view = showLoginView(IdentityProperties.defaults());

        LoginI18n i18n = LoginView.loginI18n(new IdentityTexts(MESSAGES, IdentityProperties.defaults()));
        assertThat(i18n.getForm().getTitle()).isEqualTo("Anmelden");
        assertThat(i18n.getForm().getUsername()).isEqualTo("E-Mail-Adresse");
        assertThat(i18n.getErrorMessage().getTitle()).isEqualTo("Anmeldung fehlgeschlagen");
        assertThat(view.getPageTitle()).isEqualTo("Anmelden");
    }

    @Test
    void englishIsUsedWhenTheApplicationAsksForIt() {
        IdentityProperties english = properties(IdentityProperties.defaults(), Locale.ENGLISH);
        LoginView view = showLoginView(english);

        LoginI18n i18n = LoginView.loginI18n(new IdentityTexts(MESSAGES, english));
        assertThat(i18n.getForm().getTitle()).isEqualTo("Sign in");
        assertThat(i18n.getForm().getForgotPassword()).isEqualTo("Forgot your password?");
        assertThat(view.getPageTitle()).isEqualTo("Sign in");
    }

    @Test
    void theForgotPasswordButtonLeadsToTheForgotPasswordView() {
        LoginView view = showLoginView(IdentityProperties.defaults());

        _click(_get(view, Button.class, spec -> spec.withId(LoginView.FORGOT_PASSWORD_LINK_ID)));

        assertThat(currentPath()).isEqualTo(IdentityRoutes.FORGOT_PASSWORD);
    }

    @Test
    void aLanguageWithoutItsOwnFileFallsBackToEnglish() {
        LoginView view = showLoginView(properties(IdentityProperties.defaults(), Locale.FRENCH));

        assertThat(view.getPageTitle()).isEqualTo("Sign in");
    }

    @Test
    void theRegistrationLinkIsHiddenWhenSelfRegistrationIsOff() {
        registrationService.selfRegistrationEnabled = false;
        LoginView view = showLoginView(IdentityProperties.defaults());

        assertThat(_find(view, Button.class)).noneMatch(button -> "login-register-button".equals(button.getId().orElse("")));
    }

    @Test
    void theResendLinkIsHiddenWithoutEmailVerification() {
        registrationService.emailVerificationRequired = false;
        LoginView view = showLoginView(IdentityProperties.defaults());

        assertThat(_find(view, Button.class))
                .noneMatch(button -> LoginView.RESEND_VERIFICATION_LINK_ID.equals(button.getId().orElse("")));
    }

    @Test
    void bothSideRoutesAreOfferedInTheDefaultSetup() {
        LoginView view = showLoginView(IdentityProperties.defaults());

        // Register as a button of its own, the two ways out as footer links.
        assertThat(_find(view, Button.class, spec -> spec.withId(LoginView.REGISTER_BUTTON_ID))).hasSize(1);
        assertThat(_find(view, Button.class, spec -> spec.withId(LoginView.FORGOT_PASSWORD_LINK_ID))).hasSize(1);
        assertThat(_find(view, Button.class, spec -> spec.withId(LoginView.RESEND_VERIFICATION_LINK_ID))).hasSize(1);

        _click(_get(view, Button.class, spec -> spec.withId(LoginView.REGISTER_BUTTON_ID)));
        assertThat(currentPath()).isEqualTo(IdentityRoutes.REGISTER);
    }

    @Test
    void theResendButtonLeadsToTheResendView() {
        LoginView view = showLoginView(IdentityProperties.defaults());

        _click(_get(view, Button.class, spec -> spec.withId(LoginView.RESEND_VERIFICATION_LINK_ID)));

        assertThat(currentPath()).isEqualTo(IdentityRoutes.RESEND_VERIFICATION);
    }

    /** Passkeys are an option per person; without the setting there is no button. */
    @Test
    void thePasskeyButtonAppearsOnlyWhenPasskeysAreSwitchedOn() {
        LoginView plain = showLoginView(IdentityProperties.defaults());
        assertThat(_find(plain, Button.class, spec -> spec.withId(LoginView.PASSKEY_BUTTON_ID))).isEmpty();

        LoginView withPasskeys = showLoginView(IdentityProperties.defaults()
                .withPasskeys(PasskeySettings.defaults().enabled(true)));
        Button button = _get(withPasskeys, Button.class, spec -> spec.withId(LoginView.PASSKEY_BUTTON_ID));
        assertThat(button.getText()).isEqualTo("Mit Passkey anmelden");
        assertThat(button.getWidth()).as("lines up with the form").isEqualTo("100%");
        assertThat(_get(withPasskeys, LoginForm.class)).as("the password form stays").isNotNull();
    }

    /**
     * The button starts the ceremony in the browser; the view only queues the
     * script and listens for its answer. On success the script navigates
     * away itself, so nothing happens here; a rejection becomes a text.
     */
    @Test
    void thePasskeyButtonStartsTheCeremonyInTheBrowserAndShowsItsFailure() {
        LoginView view = showLoginView(IdentityProperties.defaults()
                .withPasskeys(PasskeySettings.defaults().enabled(true)));

        _click(_get(view, Button.class, spec -> spec.withId(LoginView.PASSKEY_BUTTON_ID)));

        PendingJavaScriptInvocation call = passkeyCall("");
        // Vaadin appends the element itself as the last parameter ($this).
        assertThat(call.getInvocation().getParameters().subList(0, 3))
                .as("context path, CSRF header, CSRF token -- none of them in a test")
                .containsExactly("", "", "");

        browserRejects(call, "cancelled");
        assertThat(notificationTexts()).containsExactly("Die Anmeldung mit Passkey wurde abgebrochen.");
    }

    /** The failures of the ceremony come back as one word each and are shown as texts. */
    @Test
    void thePasskeyFailuresAreNamed() {
        LoginView view = showLoginView(IdentityProperties.defaults()
                .withPasskeys(PasskeySettings.defaults().enabled(true)));

        view.onPasskeyError("Error: cancelled");
        view.onPasskeyError("disabled");
        view.onPasskeyError("unsupported");
        view.onPasskeyError(null);

        assertThat(notificationTexts()).containsExactly(
                "Die Anmeldung mit Passkey wurde abgebrochen.",
                "Dieses Konto ist gesperrt. Bitte wende dich an die Administration.",
                "Dieser Browser unterstützt keine Passkeys.",
                "Die Anmeldung mit diesem Passkey hat nicht geklappt. Bitte melde dich mit deinem Passwort an.");
    }

    /**
     * A turned-down request brings its status along. It is the one failure
     * that cannot be seen from the screen -- the script sends it so that the
     * server log has it -- and it must still read as the catch-all, or the
     * person would be shown a raw code.
     */
    @Test
    void aRejectedRequestKeepsItsStatusAndStillReadsAsTheCatchAll() {
        LoginView view = showLoginView(IdentityProperties.defaults()
                .withPasskeys(PasskeySettings.defaults().enabled(true)));

        view.onPasskeyError("failed HTTP 400");

        assertThat(notificationTexts()).containsExactly(
                "Die Anmeldung mit diesem Passkey hat nicht geklappt. Bitte melde dich mit deinem Passwort an.");
    }

    /**
     * The offer starts with the page, not with a click -- that is the whole
     * point: whoever has a passkey is one touch away instead of three.
     */
    @Test
    void theOfferStartsWithTheViewAndOnlyWhenPasskeysAreOn() {
        showLoginView(WITH_PASSKEYS);

        assertThat(passkeyCall("conditional")).as("queued without anyone clicking").isNotNull();

        showLoginView(IdentityProperties.defaults());
        assertThat(browserCallsContaining("navigator.credentials.get"))
                .as("switched off, nothing is offered")
                .hasSize(1);
    }

    /**
     * An offer nobody took up says nothing. The script resolves quietly for
     * every such outcome -- no conditional support, no passkey on the device,
     * the password chosen instead -- because a message for something that was
     * never asked for is noise on a page everybody sees.
     */
    @Test
    void anOfferNobodyTookUpSaysNothing() {
        showLoginView(WITH_PASSKEYS);

        browserResolves(passkeyCall("conditional"), PasskeyScripts.QUIET);

        assertThat(notificationTexts()).isEmpty();
    }

    /** A passkey the person really chose still speaks when the server turns it down. */
    @Test
    void aChosenPasskeyThatFailsIsStillNamed() {
        showLoginView(WITH_PASSKEYS);

        browserRejects(passkeyCall("conditional"), "disabled");

        assertThat(notificationTexts())
                .containsExactly("Dieses Konto ist gesperrt. Bitte wende dich an die Administration.");
    }

    /**
     * The button has to end a waiting offer first: a browser turns down a
     * second {@code navigator.credentials.get} while one is pending, so
     * without this the button would fail for exactly the people the offer did
     * not reach.
     */
    @Test
    void theButtonEndsAWaitingOfferBeforeItAsks() {
        LoginView view = showLoginView(WITH_PASSKEYS);

        _click(_get(view, Button.class, spec -> spec.withId(LoginView.PASSKEY_BUTTON_ID)));

        // The ceremony script mentions the controller too (it parks it), so
        // the abort is the one script that does NOT run a ceremony.
        List<PendingJavaScriptInvocation> aborts = browserCallsContaining("__zsPasskeyConditional").stream()
                .filter(call -> !call.getInvocation().getExpression().contains("navigator.credentials.get"))
                .toList();

        assertThat(aborts).as("the waiting offer is ended").hasSize(1);
        assertThat(passkeyCall("")).as("and the button's own ceremony follows").isNotNull();
    }

    /**
     * The button can go where the offer in the username field is enough. The
     * offer itself must not go with it -- it is the whole point of switching
     * the button off.
     */
    @Test
    void theButtonCanBeSwitchedOffWhileTheOfferStays() {
        LoginView view = showLoginView(IdentityProperties.defaults()
                .withPasskeys(PasskeySettings.defaults().enabled(true).loginButton(false)));

        assertThat(_find(view, Button.class, spec -> spec.withId(LoginView.PASSKEY_BUTTON_ID)))
                .as("switched off by the application")
                .isEmpty();
        assertThat(passkeyCall("conditional")).as("the offer is still made").isNotNull();
    }

    /**
     * The ranking the page was rebuilt for: one primary button in the form,
     * one bordered button that carries someone without an account, and the
     * two ways out below as small links -- not four boxes of equal weight.
     */
    @Test
    void theWaysOutSitQuietlyInAFooter() {
        LoginView view = showLoginView(IdentityProperties.defaults());

        Button forgot = _get(view, Button.class, spec -> spec.withId(LoginView.FORGOT_PASSWORD_LINK_ID));
        Button resend = _get(view, Button.class, spec -> spec.withId(LoginView.RESEND_VERIFICATION_LINK_ID));

        assertThat(forgot.getClassNames()).contains(IdentityFormView.FOOTER_LINK_CLASS);
        assertThat(resend.getClassNames()).contains(IdentityFormView.FOOTER_LINK_CLASS);
        assertThat(forgot.getWidth()).as("a way out does not fill the column").isNull();
        assertThat(_find(view, Div.class, spec -> spec.withClasses(IdentityFormView.VIEW_CLASS + "__footer")))
                .as("both sit in one row, so they can wrap together on a narrow screen")
                .hasSize(1);
        assertThat(_get(view, LoginForm.class).isForgotPasswordButtonVisible())
                .as("the form's own link moved into the footer")
                .isFalse();
    }

    /**
     * Found in two applications independently after 0.14.0: the tertiary
     * variant leaves Vaadin 25's grey box in place, and a separator stayed
     * behind at the end of the line once the second link wrapped.
     */
    @Test
    void theFooterLinksLookLikeLinksAndNeedNoSeparator() {
        LoginView view = showLoginView(IdentityProperties.defaults());

        Button forgot = _get(view, Button.class, spec -> spec.withId(LoginView.FORGOT_PASSWORD_LINK_ID));
        IdentityFormView.FOOTER_LINK_STYLE.forEach((property, value) ->
                assertThat(forgot.getStyle().get(property)).as(property).isEqualTo(value));
        Div footer = _get(view, Div.class, spec -> spec.withClasses(IdentityFormView.VIEW_CLASS + "__footer"));
        assertThat(footer.getChildren().toList())
                .as("nothing but the two links -- the gap separates them")
                .hasSize(2)
                .allMatch(child -> child.getElement().getClassList().contains(IdentityFormView.FOOTER_LINK_CLASS));
    }

    /** Spring Security appends {@code ?error} after a failed sign-in. */
    @Test
    void theErrorParameterSwitchesTheFormIntoItsErrorState() {
        LoginView view = showLoginView(IdentityProperties.defaults());
        LoginForm form = _get(view, LoginForm.class);
        assertThat(form.isError()).isFalse();

        BeforeEnterEvent event = enterEventWith(IdentityRoutes.LOGIN, Map.of("error", List.of("")));
        view.beforeEnter(event);

        assertThat(form.isError()).isTrue();
    }

    @Test
    void withoutTheErrorParameterTheFormStaysClean() {
        LoginView view = showLoginView(IdentityProperties.defaults());

        view.beforeEnter(enterEventWith(IdentityRoutes.LOGIN, Map.of()));

        assertThat(_get(view, LoginForm.class).isError()).isFalse();
    }

    private LoginView showWithProviders(ExternalProvider... providers) {
        List<ExternalProvider> offered = List.of(providers);
        return show(new LoginView(registrationService, IdentityProperties.defaults(), MESSAGES, () -> offered));
    }

    @Test
    void everyOfferedProviderGetsAButtonInItsOrder() {
        LoginView view = showWithProviders(new ExternalProvider("google", "Google"),
                new ExternalProvider("github", "GitHub"));

        List<Button> buttons = _find(view, Button.class).stream()
                .filter(button -> button.getId().orElse("").startsWith(LoginView.EXTERNAL_BUTTON_ID_PREFIX))
                .toList();
        assertThat(buttons).extracting(Button::getText).containsExactly("Mit Google anmelden", "Mit GitHub anmelden");
    }

    @Test
    void withoutProvidersThereIsNoButton() {
        LoginView view = show(new LoginView(registrationService, IdentityProperties.defaults(), MESSAGES,
                ExternalProviders.none()));

        assertThat(_find(view, Button.class, spec -> spec.withId(LoginView.EXTERNAL_BUTTON_ID_PREFIX + "google")))
                .isEmpty();
    }

    /** The address belongs to Spring Security, not to a view: a full page load, not the router. */
    @Test
    void aProviderButtonLeavesThePageForTheProvider() {
        LoginView view = showWithProviders(new ExternalProvider("google", "Google"));

        _click(_get(view, Button.class, spec -> spec.withId(LoginView.EXTERNAL_BUTTON_ID_PREFIX + "google")));

        assertThat(browserCall("window.open").getInvocation().getParameters())
                .contains("oauth2/authorization/google");
    }

    @Test
    void aRefusedProviderSignInSaysWhyInsteadOfBlamingThePassword() {
        LoginView view = showWithProviders(new ExternalProvider("google", "Google"));

        view.beforeEnter(enterEventWith(IdentityRoutes.LOGIN, Map.of("error", List.of(""),
                IdentityPaths.EXTERNAL_ERROR_PARAMETER, List.of("no-account"))));

        Paragraph reason = _get(view, Paragraph.class, spec -> spec.withId(LoginView.EXTERNAL_ERROR_ID));
        assertThat(reason.isVisible()).isTrue();
        assertThat(reason.getText()).contains("noch kein Konto");
        assertThat(_get(view, LoginForm.class).isError()).as("the form's text is about passwords").isFalse();
    }

    @Test
    void anUnknownReasonGetsTheGeneralText() {
        LoginView view = showWithProviders();

        view.beforeEnter(enterEventWith(IdentityRoutes.LOGIN,
                Map.of(IdentityPaths.EXTERNAL_ERROR_PARAMETER, List.of("whatever"))));

        assertThat(_get(view, Paragraph.class, spec -> spec.withId(LoginView.EXTERNAL_ERROR_ID)).getText())
                .contains("hat nicht geklappt");
    }

    @Test
    void theReasonStaysHiddenOtherwise() {
        LoginView view = showWithProviders();

        view.beforeEnter(enterEventWith(IdentityRoutes.LOGIN, Map.of()));

        assertThat(_find(view, Paragraph.class, spec -> spec.withId(LoginView.EXTERNAL_ERROR_ID)))
                .as("Karibu finds visible components only")
                .isEmpty();
    }
}
