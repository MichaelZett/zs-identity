package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.RegistrationService;
import de.zettsystems.identity.values.IdentityProperties;

/**
 * Fordert die Bestätigungsmail erneut an.
 *
 * <p>Ohne diesen Weg ist ein Konto verloren, dessen Bestätigungsmail nicht
 * angekommen ist: Die Anmeldung scheitert am gesperrten Konto, und der Token
 * liegt nur als Hash in der Datenbank — der Link lässt sich also nirgends
 * nachschlagen.
 *
 * <p>Wie beim Zurücksetzen des Passworts ist die Rückmeldung immer dieselbe,
 * egal ob es zu der Adresse ein Konto gibt. Der Dienst schweigt aus demselben
 * Grund: Sonst ließe sich hier durchprobieren, wer registriert ist.
 */
@Route(value = IdentityRoutes.RESEND_VERIFICATION, autoLayout = false)
@AnonymousAllowed
public class ResendVerificationView extends IdentityFormView {

    private final RegistrationService registrationService;
    private final EmailField email = new EmailField();

    public ResendVerificationView(RegistrationService registrationService, IdentityProperties properties,
                                  IdentityMessages messages) {
        super(messages, properties, "resend-verification");
        this.registrationService = registrationService;

        add(heading("identity.resend.title"));
        add(paragraph("identity.resend.intro"));

        email.setLabel(text("identity.common.email"));
        email.setRequiredIndicatorVisible(true);
        email.setAutocomplete(Autocomplete.USERNAME);
        email.setId("resend-email-field");

        Button submit = primaryButton("identity.resend.submit", "resend-submit-button", event -> submit());

        addFullWidth(email, submit, backToLoginButton());
    }

    @Override
    public String getPageTitle() {
        return text("identity.resend.pageTitle");
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

        registrationService.resendVerification(email.getValue());
        showConfirmation();
    }

    private void showConfirmation() {
        removeAll();
        add(heading("identity.common.mailSent.title"));
        add(new Paragraph(text("identity.resend.sent", email.getValue())
                + " " + text("identity.common.spamHint")));
        addFullWidth(backToLoginButton());
    }
}
