package de.zettsystems.identity.ui;

import com.github.mvysny.kaributesting.v10.LoginFormKt;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.router.BeforeEnterEvent;
import de.zettsystems.identity.values.IdentityProperties;
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

    private LoginView showLoginView(IdentityProperties properties) {
        return show(new LoginView(registrationService, properties, MESSAGES));
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

        LoginFormKt._forgotPassword(_get(view, LoginForm.class));

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
                .noneMatch(button -> "login-resend-verification-button".equals(button.getId().orElse("")));
    }

    @Test
    void bothSideRoutesAreOfferedInTheDefaultSetup() {
        LoginView view = showLoginView(IdentityProperties.defaults());

        assertThat(_find(view, Button.class)).hasSize(2);

        _click(_get(view, Button.class, spec -> spec.withId("login-register-button")));
        assertThat(currentPath()).isEqualTo(IdentityRoutes.REGISTER);
    }

    @Test
    void theResendButtonLeadsToTheResendView() {
        LoginView view = showLoginView(IdentityProperties.defaults());

        _click(_get(view, Button.class, spec -> spec.withId("login-resend-verification-button")));

        assertThat(currentPath()).isEqualTo(IdentityRoutes.RESEND_VERIFICATION);
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
}
