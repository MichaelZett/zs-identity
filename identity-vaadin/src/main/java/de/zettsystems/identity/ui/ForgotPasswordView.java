package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.PasswordResetService;
import de.zettsystems.identity.values.IdentityProperties;

/** Formular für "Passwort vergessen". */
@Route(value = IdentityRoutes.FORGOT_PASSWORD, autoLayout = false)
@AnonymousAllowed
public class ForgotPasswordView extends IdentityFormView {

    private final PasswordResetService passwordResetService;
    private final EmailField email = new EmailField();

    public ForgotPasswordView(PasswordResetService passwordResetService, IdentityProperties properties,
                              IdentityMessages messages) {
        super(messages, properties, "forgot-password");
        this.passwordResetService = passwordResetService;

        add(heading("identity.forgot.title"));
        add(paragraph("identity.forgot.intro"));

        email.setLabel(text("identity.common.email"));
        email.setRequiredIndicatorVisible(true);
        // Die Kennung, unter der der Passwortmanager das Zugangsdatenpaar
        // ablegt — damit er sie hier anbietet, statt sie tippen zu lassen.
        email.setAutocomplete(Autocomplete.USERNAME);
        email.setId("forgot-email-field");

        Button submit = primaryButton("identity.forgot.submit", "forgot-submit-button", event -> submit());

        addFullWidth(email, submit, backToLoginButton());
    }

    @Override
    public String getPageTitle() {
        return text("identity.forgot.pageTitle");
    }

    private Button backToLoginButton() {
        return navigationButton("identity.common.backToLogin", IdentityRoutes.LOGIN);
    }

    private void submit() {
        if (email.isEmpty() || email.isInvalid()) {
            email.setErrorMessage(text("identity.common.invalidEmail"));
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
        add(heading("identity.common.mailSent.title"));
        add(new Paragraph(text("identity.forgot.sent", email.getValue())
                + " " + text("identity.common.spamHint")));
        addFullWidth(backToLoginButton());
    }
}
