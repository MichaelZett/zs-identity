package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
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
 *
 * <p>Die einzige Ansicht, die {@link #centerOnPage()} benutzt: Sie ist so
 * kurz, dass sie oben am Rand kleben würde.
 */
@Route(value = IdentityRoutes.LOGIN, autoLayout = false)
@AnonymousAllowed
public class LoginView extends IdentityFormView implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();

    public LoginView(RegistrationService registrationService, IdentityProperties properties,
                     IdentityMessages messages) {
        super(messages, properties, "login");
        centerOnPage();

        loginForm.setAction(IdentityRoutes.LOGIN);
        loginForm.setI18n(loginI18n(texts()));
        loginForm.setForgotPasswordButtonVisible(true);
        loginForm.addForgotPasswordListener(
                event -> getUI().ifPresent(ui -> ui.navigate(IdentityRoutes.FORGOT_PASSWORD)));

        // Die Anmeldung füllt die Seite; damit Formular und Knöpfe trotzdem
        // eine gemeinsame, begrenzte Breite haben, sitzen sie in einer Spalte.
        VerticalLayout column = centeredColumn();
        column.add(fullWidth(loginForm));

        // Der Verweis erscheint nur, wenn die Selbstregistrierung eingeschaltet
        // ist — sonst führt er auf eine Seite, die jede Eingabe ablehnt.
        if (registrationService.isSelfRegistrationEnabled()) {
            column.add(fullWidth(registerButton()));
        }
        // Ohne Bestätigungspflicht gibt es keine Bestätigungsmail — dann führt
        // der Weg ins Leere und bleibt weg.
        if (registrationService.isEmailVerificationRequired()) {
            column.add(fullWidth(resendButton()));
        }
    }

    @Override
    public String getPageTitle() {
        return text("identity.login.pageTitle");
    }

    private Button registerButton() {
        Button button = navigationButton("identity.login.register", IdentityRoutes.REGISTER);
        button.setId("login-register-button");
        return button;
    }

    private Button resendButton() {
        Button button = navigationButton("identity.login.resendVerification", IdentityRoutes.RESEND_VERIFICATION);
        button.setId("login-resend-verification-button");
        return button;
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
