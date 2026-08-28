package de.zettsystems.identity.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.RegistrationService;
import de.zettsystems.identity.values.IdentityProperties;

/**
 * Anmeldeseite.
 *
 * <p>Nutzt Vaadins {@code LoginForm}, weil dessen Formular an
 * {@code /login} POSTet und damit direkt von Spring Securitys
 * Formular-Anmeldung verarbeitet wird — es gibt also keinen eigenen
 * Anmeldecode, der Fehler enthalten könnte.
 */
@Route(value = IdentityRoutes.LOGIN, autoLayout = false)
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    private final LoginForm loginForm = new LoginForm();
    private final IdentityTexts texts;

    public LoginView(RegistrationService registrationService, IdentityProperties properties,
                     IdentityMessages messages) {
        this.texts = new IdentityTexts(messages, properties);

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        loginForm.setAction(IdentityRoutes.LOGIN);
        loginForm.setI18n(loginI18n(texts));
        loginForm.setForgotPasswordButtonVisible(true);
        loginForm.addForgotPasswordListener(
                event -> getUI().ifPresent(ui -> ui.navigate(IdentityRoutes.FORGOT_PASSWORD)));

        add(loginForm);

        // Der Verweis erscheint nur, wenn die Selbstregistrierung eingeschaltet
        // ist — sonst führt er auf eine Seite, die jede Eingabe ablehnt.
        if (registrationService.isSelfRegistrationEnabled()) {
            Button register = new Button(texts.get("identity.login.register"),
                    event -> UI.getCurrent().navigate(IdentityRoutes.REGISTER));
            register.setId("login-register-button");
            add(centered(register));
        }
        // Ohne Bestätigungspflicht gibt es keine Bestätigungsmail — dann führt
        // der Weg ins Leere und bleibt weg.
        if (registrationService.isEmailVerificationRequired()) {
            Button resend = new Button(texts.get("identity.login.resendVerification"),
                    event -> UI.getCurrent().navigate(IdentityRoutes.RESEND_VERIFICATION));
            resend.setId("login-resend-verification-button");
            add(centered(resend));
        }
    }

    @Override
    public String getPageTitle() {
        return texts.get("identity.login.pageTitle");
    }

    private static HorizontalLayout centered(Button button) {
        HorizontalLayout row = new HorizontalLayout(button);
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        return row;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Spring Security hängt bei fehlgeschlagener Anmeldung ?error an.
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            loginForm.setError(true);
        }
    }

    /**
     * Paket-sichtbar und statisch, damit der Test die Beschriftungen prüfen
     * kann: {@code LoginForm.getI18n()} ist geschützt, das gesetzte Objekt
     * lässt sich von außen also nicht mehr auslesen.
     */
    static LoginI18n loginI18n(IdentityTexts texts) {
        LoginI18n i18n = LoginI18n.createDefault();
        LoginI18n.Form form = i18n.getForm();
        form.setTitle(texts.get("identity.login.title"));
        form.setUsername(texts.get("identity.common.email"));
        form.setPassword(texts.get("identity.common.password"));
        form.setSubmit(texts.get("identity.login.submit"));
        form.setForgotPassword(texts.get("identity.login.forgotPassword"));
        i18n.setForm(form);

        LoginI18n.ErrorMessage error = i18n.getErrorMessage();
        error.setTitle(texts.get("identity.login.error.title"));
        error.setMessage(texts.get("identity.login.error.message"));
        i18n.setErrorMessage(error);
        return i18n;
    }
}
