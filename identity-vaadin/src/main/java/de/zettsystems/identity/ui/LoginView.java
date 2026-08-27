package de.zettsystems.identity.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.RegistrationService;

/**
 * Anmeldeseite.
 *
 * <p>Nutzt Vaadins {@code LoginForm}, weil dessen Formular an
 * {@code /login} POSTet und damit direkt von Spring Securitys
 * Formular-Anmeldung verarbeitet wird — es gibt also keinen eigenen
 * Anmeldecode, der Fehler enthalten könnte.
 */
@Route(value = IdentityRoutes.LOGIN, autoLayout = false)
@PageTitle("Anmelden")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();

    public LoginView(RegistrationService registrationService) {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        loginForm.setAction(IdentityRoutes.LOGIN);
        loginForm.setI18n(germanI18n());
        loginForm.setForgotPasswordButtonVisible(true);
        loginForm.addForgotPasswordListener(
                event -> getUI().ifPresent(ui -> ui.navigate(IdentityRoutes.FORGOT_PASSWORD)));

        add(loginForm);

        // Der Verweis erscheint nur, wenn die Selbstregistrierung eingeschaltet
        // ist — sonst führt er auf eine Seite, die jede Eingabe ablehnt.
        if (registrationService.isSelfRegistrationEnabled()) {
            add(centered(new Button("Noch kein Konto? Jetzt registrieren",
                    event -> UI.getCurrent().navigate(IdentityRoutes.REGISTER))));
        }
        // Ohne Bestätigungspflicht gibt es keine Bestätigungsmail — dann führt
        // der Weg ins Leere und bleibt weg.
        if (registrationService.isEmailVerificationRequired()) {
            Button resend = new Button("Bestätigungsmail nicht erhalten?",
                    event -> UI.getCurrent().navigate(IdentityRoutes.RESEND_VERIFICATION));
            resend.setId("login-resend-verification-button");
            add(centered(resend));
        }
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

    private static LoginI18n germanI18n() {
        LoginI18n i18n = LoginI18n.createDefault();
        LoginI18n.Form form = i18n.getForm();
        form.setTitle("Anmelden");
        form.setUsername("E-Mail-Adresse");
        form.setPassword("Passwort");
        form.setSubmit("Anmelden");
        form.setForgotPassword("Passwort vergessen?");
        i18n.setForm(form);

        LoginI18n.ErrorMessage error = i18n.getErrorMessage();
        error.setTitle("Anmeldung fehlgeschlagen");
        error.setMessage("E-Mail-Adresse oder Passwort stimmen nicht. "
                + "Wenn du dich gerade erst registriert hast, bestätige zuerst den Link in deiner E-Mail.");
        i18n.setErrorMessage(error);
        return i18n;
    }
}
