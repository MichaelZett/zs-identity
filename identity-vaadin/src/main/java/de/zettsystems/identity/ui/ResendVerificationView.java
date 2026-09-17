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
 * Requests the verification mail again.
 *
 * <p>Without this route an account whose verification mail never arrived is
 * lost: sign-in fails because the account is blocked, and the token is stored
 * only as a hash, so the link cannot be looked up anywhere.
 *
 * <p>As with the password reset, the feedback is always the same whether or
 * not an account exists for the address. The service stays silent for the same
 * reason: otherwise this could be used to work out who is registered.
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
