package de.zettsystems.identity.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.PasswordResetService;

/** Formular für "Passwort vergessen". */
@Route(value = IdentityRoutes.FORGOT_PASSWORD, autoLayout = false)
@PageTitle("Passwort vergessen")
@AnonymousAllowed
public class ForgotPasswordView extends VerticalLayout {

    private final PasswordResetService passwordResetService;
    private final EmailField email = new EmailField("E-Mail-Adresse");

    public ForgotPasswordView(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;

        setMaxWidth("28rem");
        getStyle().set("margin", "0 auto");

        add(new H2("Passwort zurücksetzen"));
        add(new Paragraph("Gib deine E-Mail-Adresse ein. Wenn es dazu ein Konto gibt, "
                + "schicken wir dir einen Link zum Setzen eines neuen Passworts."));

        email.setRequiredIndicatorVisible(true);
        email.setWidthFull();

        Button submit = new Button("Link anfordern", event -> submit());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setWidthFull();

        add(email, submit, backToLoginButton());
    }

    /** Navigations-Button zur Anmeldung — volle Breite, weil das Formular schmal ist. */
    private static Button backToLoginButton() {
        Button button = new Button("Zurück zur Anmeldung",
                event -> UI.getCurrent().navigate(IdentityRoutes.LOGIN));
        button.setWidthFull();
        return button;
    }

    private void submit() {
        if (email.isEmpty() || email.isInvalid()) {
            email.setErrorMessage("Bitte gib eine gültige E-Mail-Adresse ein.");
            email.setInvalid(true);
            return;
        }

        passwordResetService.requestReset(email.getValue());
        showConfirmation();
    }

    /**
     * Zeigt dieselbe Bestätigung, egal ob es zu der Adresse ein Konto gibt.
     * Eine ehrlichere Meldung wäre hier ein Sicherheitsproblem: Über dieses
     * Formular ließe sich sonst durchprobieren, wer bei uns registriert ist.
     */
    private void showConfirmation() {
        removeAll();
        add(new H2("E-Mail unterwegs"));
        add(new Paragraph("Wenn es zu %s ein Konto gibt, ist der Link jetzt unterwegs. "
                .formatted(email.getValue())
                + "Schau auch im Spam-Ordner nach."));
        add(backToLoginButton());
    }
}
